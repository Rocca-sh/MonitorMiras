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
import java.util.List;
import miras.monitor.Utils.RedisDvrService;
import miras.monitor.Utils.WvpApiServ;
import miras.monitor.Exceptions.BadRequest.BadRequestException;
import miras.monitor.Zlmedia.Repo.ZlmVideoRepo;
import miras.monitor.lib.SipCreator;


@Service
public class DvrServImp implements DvrServ {

    private final DvrPg dvrPg;
    private final OrgPg orgPg;
    private final UserPg userPg;
    private final RedisDvrService redisDvrService;
    private final WvpApiServ wvpApiServ;
    private final ZlmVideoRepo zlmVideoRepo;

    @Autowired
    public DvrServImp(DvrPg dvrPg, OrgPg orgPg, UserPg userPg, RedisDvrService redisDvrService, WvpApiServ wvpApiServ, ZlmVideoRepo zlmVideoRepo) {
        this.dvrPg = dvrPg;
        this.orgPg = orgPg;
        this.userPg = userPg;
        this.redisDvrService = redisDvrService;
        this.wvpApiServ = wvpApiServ;
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
        if (!dvr.getOrganization().getUlid().equals(orgUlid)) {
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
        if (!dvr.getOrganization().getUlid().equals(orgUlid)) {
            throw new UnauthorizedException("No tienes permiso sobre este DVR");
        }
        dvrPg.delete(dvr);
    }

    @Override
    public List<Dvr> listDevices(String orgUlid) {
        if (orgUlid == null || orgUlid.isEmpty()) return List.of();
        return dvrPg.findByOrganizationUlid(orgUlid);
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
        
        return wvpApiServ.getDeviceChannels(sipId);
    }

    @Override
    public List<String> playVideo(String dvrSipId, String channelSipId, String orgUlid) {
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
}
