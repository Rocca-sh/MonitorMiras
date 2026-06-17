package miras.monitor.Utils;

import miras.monitor.Dvr.Model.Repo.DvrPg;
import miras.monitor.Exceptions.NotFound.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;
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

    private String getOnlineDvrsKey(String idOrgOwner) {
        return "org:" + idOrgOwner + ":online_dvrs";
    }

    public void addDvrOnline(String idOrgOwner, String idDvr) {
        redisTemplate.opsForSet().add(getOnlineDvrsKey(idOrgOwner), idDvr);
    }

    public void removeDvrOnline(String idOrgOwner, String idDvr) {
        redisTemplate.opsForSet().remove(getOnlineDvrsKey(idOrgOwner), idDvr);
    }

    public Set<String> getOnlineDvrsByOrg(String idOrgOwner) {
        return redisTemplate.opsForSet().members(getOnlineDvrsKey(idOrgOwner));
    }

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

    /**
     * Fuerza una búsqueda manual en la BD 0 (WVP) para ver si la cámara ya está transmitiendo.
     * Si lo está, la guarda en el caché y la añade a la lista de encendidos.
     */
    public boolean checkAndSyncDvrStatus(String sipId, String orgId) {
        // Consultamos directo en la BD 0 de WVP si existe la llave
        Boolean isOnline = wvpRedisTemplate.hasKey("VVSK_WVP_DEVICE_ONLINE_" + sipId);
        
        if (Boolean.TRUE.equals(isOnline)) {
            // Lo agregamos a la lista de encendidos
            addDvrOnline(orgId, sipId);
            // Refrescamos su etiqueta en caché por 24 horas
            redisTemplate.opsForValue().set(getMappingKey(sipId), orgId, 24, TimeUnit.HOURS);
            return true;
        }
        return false;
    }
}
