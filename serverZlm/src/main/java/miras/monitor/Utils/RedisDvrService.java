package miras.monitor.Utils;

import miras.monitor.Dvr.Model.Repo.DvrPg;
import miras.monitor.Exceptions.NotFound.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class RedisDvrService {

    @Autowired
    private StringRedisTemplate redisTemplate; // Este apunta a la BD 1 (Nuestra App)

    @Autowired
    @Qualifier("wvpRedisTemplate")
    private StringRedisTemplate wvpRedisTemplate; // Este apunta a la BD 0 (WVP)

    @Autowired
    private DvrPg dvrPg;

    /**
     * Guarda la IP y Puerto del DVR cuando se registra o manda latido.
     * Le pone un tiempo de vida (TTL) de 3 minutos. Si el DVR se desconecta y no 
     * manda su latido (Keepalive), Redis lo borrará automáticamente.
     */
    public void registerDvr(String sipId, String ip, int port) {
        String key = "dvr:" + sipId;
        redisTemplate.opsForValue().set(key, ip + ":" + port, 3, TimeUnit.MINUTES);
    }

    /**
     * Refresca el tiempo de vida cuando llega un mensaje de latido (Keepalive).
     */
    public void refreshDvr(String sipId) {
        String key = "dvr:" + sipId;
        redisTemplate.expire(key, 3, TimeUnit.MINUTES);
    }

    /**
     * Obtiene la IP y puerto del DVR para mandarle el video.
     * Retorna null si el DVR está desconectado (si la llave expiró).
     */
    public String getDvrAddress(String sipId) {
        return redisTemplate.opsForValue().get("dvr:" + sipId);
    }

    // ==========================================
    // Compatibilidad con controladores antiguos
    // ==========================================

    public java.util.Set<String> getOnlineDvrsByOrg(String idOrgOwner) {
        java.util.Set<String> keys = redisTemplate.keys("dvr:" + idOrgOwner + "*");
        java.util.Set<String> sipIds = new java.util.HashSet<>();
        if (keys != null) {
            for (String key : keys) {
                sipIds.add(key.replace("dvr:", ""));
            }
        }
        return sipIds;
    }

    @Deprecated
    public void addDvrOnline(String idOrgOwner, String idDvr) {
        // Dummy por compatibilidad con WVP viejo
        registerDvr(idDvr, "127.0.0.1", 5060); 
    }

    @Deprecated
    public void removeDvrOnline(String idOrgOwner, String idDvr) {
        redisTemplate.delete("dvr:" + idDvr);
    }

    // ==========================================
    // Métodos Antiguos de WVP (Se pueden borrar luego si no se usan)
    // ==========================================
    
    private String getMappingKey(String sipId) {
        return "mapeo:sip:" + sipId;
    }

    public String getOrgIdBySipId(String sipId) {
        String cacheKey = getMappingKey(sipId);
        String cachedOrgId = redisTemplate.opsForValue().get(cacheKey);
        
        if (cachedOrgId != null) {
            return cachedOrgId;
        }

        return dvrPg.findBySipId(sipId).map(dvr -> {
            String id = dvr.getOrganization().getUlid();
            redisTemplate.opsForValue().set(cacheKey, id, 24, TimeUnit.HOURS);
            return id;
        }).orElseThrow(() -> new NotFoundException("El DVR con SIP " + sipId + " no está registrado"));
    }

    public boolean checkAndSyncDvrStatus(String sipId, String orgId) {
        Boolean isOnline = wvpRedisTemplate.hasKey("VVSK_WVP_DEVICE_ONLINE_" + sipId);
        if (Boolean.TRUE.equals(isOnline)) {
            redisTemplate.opsForValue().set(getMappingKey(sipId), orgId, 24, TimeUnit.HOURS);
            return true;
        }
        return false;
    }
}
