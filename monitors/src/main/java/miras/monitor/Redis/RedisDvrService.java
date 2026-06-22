package miras.monitor.Utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import miras.monitor.Exceptions.NotFound.NotFoundException;

import java.util.concurrent.TimeUnit;
import java.util.Set;
import java.util.HashSet;

@Service
public class RedisDvrService {

    @Autowired
    private StringRedisTemplate redisTemplate; 
    public void registerDvr(String sipId, String ip, int port, int numChannels) {
        String key = "dvr:" + sipId;
        redisTemplate.opsForValue().set(key, ip + ":" + port + ":" + numChannels, 3, TimeUnit.MINUTES);
    }

    public String getDvrAddress(String sipId) {
        return redisTemplate.opsForValue().get("dvr:" + sipId);
    }

    public Set<String> getOnlineDvrsByOrg(String idOrgOwner) {
        Set<String> keys = redisTemplate.keys("dvr:" + idOrgOwner + "*");
        Set<String> sipIds = new HashSet<>();
        if (keys != null) {
            for (String key : keys) {
                sipIds.add(key.replace("dvr:", ""));
            }
        }
        return sipIds;
    }

    public String getOrgIdBySipId(String sipId) {
        if (sipId == null || sipId.length() < 10) {
            throw new NotFoundException("El SIP ID es inválido");
        }
        return sipId.substring(0, 10);
    }

    public boolean checkAndSyncDvrStatus(String sipId, String orgId) {
        return redisTemplate.hasKey("dvr:" + sipId);
    }

    public void saveChannels(String sipId, String jsonChannels) {
        String key = "dvr_channels:" + sipId;
        redisTemplate.opsForValue().set(key, jsonChannels, 3, TimeUnit.MINUTES);
    }

    public String getChannels(String sipId) {
        return redisTemplate.opsForValue().get("dvr_channels:" + sipId);
    }

    public void deleteChannels(String sipId) {
        redisTemplate.delete("dvr_channels:" + sipId);
    }

    public void setDvrOffline(String sipId) {
        redisTemplate.delete("dvr:" + sipId);
    }
}
