package miras.monitor.Dvr.Service.Imp;

import miras.monitor.Dvr.Model.Dvr;
import miras.monitor.Org.Model.Org;
import miras.monitor.Org.Model.Repo.OrgPg;
import miras.monitor.Dvr.Model.Repo.DvrPg;
import miras.monitor.Dvr.Service.DvrServ;
import miras.monitor.Exceptions.NotFound.NotFoundException;
import miras.monitor.Exceptions.UnAuthorized.UnauthorizedException;
import miras.monitor.User.Model.Repo.UserPg;
import miras.monitor.User.Model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import miras.monitor.Utils.RedisDvrService;

import miras.monitor.Exceptions.BadRequest.BadRequestException;
import miras.monitor.Zlmedia.Repo.ZlmVideoRepo;
import miras.monitor.lib.SipCreator;


@Service
public class DvrServImp implements DvrServ {

    private final DvrPg dvrPg;
    private final OrgPg orgPg;
    private final UserPg userPg;
    private final RedisDvrService redisDvrService;

    private final ZlmVideoRepo zlmVideoRepo;

    @Autowired
    public DvrServImp(DvrPg dvrPg, OrgPg orgPg, UserPg userPg, RedisDvrService redisDvrService, ZlmVideoRepo zlmVideoRepo) {
        this.dvrPg = dvrPg;
        this.orgPg = orgPg;
        this.userPg = userPg;
        this.redisDvrService = redisDvrService;
        this.zlmVideoRepo = zlmVideoRepo;
    }

    private Org getOrg(String orgUlid) {
        if (orgUlid == null || orgUlid.isEmpty()) {
            throw new UnauthorizedException("El usuario no pertenece a ninguna empresa");
        }
        return orgPg.findById(orgUlid).orElseThrow(() -> new NotFoundException("Empresa no encontrada"));
    }

    @Override
    @Transactional
    public Dvr registerDevice(Dvr dvr, String orgUlid, String userUlid) {
        if (orgUlid == null || orgUlid.isEmpty()) {
            throw new UnauthorizedException("El usuario no pertenece a ninguna empresa");
        }
        try {
            Org orgRef = orgPg.getReferenceById(orgUlid);
            User userRef = userPg.getReferenceById(userUlid);
            dvr.setOrganization(orgRef);
            dvr.setCreator(userRef);
            return dvrPg.save(dvr);
        } catch (Exception e) {
            throw new RuntimeException("No se pudo registrar el DVR: " + e.getMessage());
        }
    }

    @Override
    public Dvr updateSettings(String ulid, Dvr updatedData, String orgUlid) {
        Dvr dvr = dvrPg.findById(ulid).orElseThrow(() -> new NotFoundException("DVR no encontrado"));
        if (!dvr.getOrganization().getSipId().equals(orgUlid)) {
            throw new UnauthorizedException("No tienes permiso sobre este DVR");
        }
        
        dvr.setName(updatedData.getName());
        dvr.setProtocol(updatedData.getProtocol());
        dvr.setSipId(updatedData.getSipId());
        
        return dvrPg.save(dvr);
    }

    @Override
    public void removeDevice(String ulid, String orgUlid) {
        Dvr dvr = dvrPg.findById(ulid).orElseThrow(() -> new NotFoundException("DVR no encontrado"));
        if (!dvr.getOrganization().getSipId().equals(orgUlid)) {
            throw new UnauthorizedException("No tienes permiso sobre este DVR");
        }
        dvrPg.delete(dvr);
    }

    @Override
    public String getDvrChannels(String sipId, String orgUlid) {
        String cachedOrgId = redisDvrService.getOrgIdBySipId(sipId);
        
        if (!cachedOrgId.equals(orgUlid)) {
            throw new UnauthorizedException("No tienes permiso sobre este DVR");
        }
        
        if (!redisDvrService.getOnlineDvrsByOrg(orgUlid).contains(sipId)) {
            throw new BadRequestException("El DVR no se encuentra online");
        }
        
        // TODO: Implementar lógica nativa para obtener catálogo de canales del DVR mediante SIP
        return "[]";
    }

    @Override
    public Map<String, String> playVideo(String dvrSipId, String channelSipId, String orgUlid) {
        /*  1. Validar que el OrgID corresponda a los primeros 10 dígitos del DVR
        //String extractedOrg = SipCreator.getOrgIdFromSip(dvrSipId);
        //if (extractedOrg == null || !extractedOrg.equals(orgUlid)) {
        //    throw new UnauthorizedException("No tienes permiso sobre este DVR");
        //}
        */

        String dvrAddress = redisDvrService.getDvrAddress(dvrSipId);
        if (dvrAddress == null){
            throw new BadRequestException("El DVR no se encuentra online");
        }

        String[] parts = dvrAddress.split(":");
        String ip = parts[0];
        int port = Integer.parseInt(parts[1]);

        // Si no mandan canal, asumimos que es el SIP del DVR base
        String targetChannel = (channelSipId != null && !channelSipId.isEmpty()) ? channelSipId : dvrSipId;

        // 4. Mandar pedir los links al Repo de ZLM
        return zlmVideoRepo.getPlaybackLinks(targetChannel, ip, port);
    }

    @Override
    public Map<String, Map<String, String>> playVideoBatch(String dvrSipId, List<String> channelIds, String orgUlid) {
        String dvrAddress = redisDvrService.getDvrAddress(dvrSipId);
        if (dvrAddress == null){
            throw new BadRequestException("El DVR no se encuentra online");
        }

        String[] parts = dvrAddress.split(":");
        String ip = parts[0];
        int port = Integer.parseInt(parts[1]);

        Map<String, Map<String, String>> result = new ConcurrentHashMap<>();

        List<CompletableFuture<Void>> futures = channelIds.stream()
            .map(channelId -> CompletableFuture.runAsync(() -> {
                try {
                    Map<String, String> links = zlmVideoRepo.getPlaybackLinks(channelId, ip, port);
                    result.put(channelId, links);
                } catch (Exception e) {
                    System.err.println("Error procesando canal " + channelId + ": " + e.getMessage());
                }
            }))
            .collect(Collectors.toList());

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        return result;
    }

    @Override
    public void stopVideo(String channelSipId, String orgUlid) {
        if (channelSipId == null || channelSipId.isEmpty()) {
            throw new BadRequestException("El channelSipId es obligatorio");
        }
        zlmVideoRepo.stopStream(channelSipId);
    }

    @Override
    public List<Map<String, Object>> listDevices(String orgSipId) {
        Set<String> sipIdsOnline = redisDvrService.getOnlineDvrsByOrg(orgSipId);
        List<Dvr> dvrsRegistrados = dvrPg.findByOrganizationSipId(orgSipId);
        
        Map<String, Dvr> mapaRegistrados = new HashMap<>();
        for (Dvr dvr : dvrsRegistrados) {
            mapaRegistrados.put(dvr.getSipId(), dvr);
        }
        
        List<Map<String, Object>> result = new ArrayList<>();
        
        // Agregar todos los que están online (Tengan nombre o no)
        for (String sipId : sipIdsOnline) {
            String addressInfo = redisDvrService.getDvrAddress(sipId);
            String[] parts = addressInfo != null ? addressInfo.split(":") : new String[]{"", "", "1"};
            
            Map<String, Object> info = new HashMap<>();
            info.put("sipId", sipId);
            info.put("ip", parts.length > 0 ? parts[0] : "");
            info.put("port", parts.length > 1 ? parts[1] : "");
            info.put("channels", parts.length > 2 ? parts[2] : "1");
            info.put("isOnline", true);
            
            if (mapaRegistrados.containsKey(sipId)) {
                info.put("name", mapaRegistrados.get(sipId).getName());
                info.put("isAssigned", true);
                info.put("dvrUlid", mapaRegistrados.get(sipId).getUlid());
            } else {
                info.put("name", "Sin asignar");
                info.put("isAssigned", false);
            }
            result.add(info);
        }
        
        // Opcional: Agregar los que están en Postgres pero NO están online (Para que sepas que existen pero están caídos)
        for (Dvr dvr : dvrsRegistrados) {
            if (!sipIdsOnline.contains(dvr.getSipId())) {
                Map<String, Object> info = new HashMap<>();
                info.put("sipId", dvr.getSipId());
                info.put("ip", ""); // Offline
                info.put("port", "");
                info.put("channels", "1");
                info.put("isOnline", false);
                info.put("name", dvr.getName());
                info.put("isAssigned", true);
                info.put("dvrUlid", dvr.getUlid());
                result.add(info);
            }
        }
        
        return result;
    }
}
