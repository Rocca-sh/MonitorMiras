package miras.monitor.Utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RedisService {

    private final StringRedisTemplate redisTemplate;

    @Autowired
    public RedisService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public String genCode(String key) {
        String code = String.format("%06d", new java.util.Random().nextInt(999999));
        redisTemplate.opsForValue().set(key, code, Duration.ofMinutes(10));
        return code;
    }

    public boolean verifyCode(String key, String val) {
        String storedVal = redisTemplate.opsForValue().get(key);
        if (storedVal != null && storedVal.equals(val)) {
            redisTemplate.delete(key);
            return true;
        }
        return false;
    }

    public void setDvrStatus(String dvrUlid, boolean isOnline) {
        String key = "dvr:status:" + dvrUlid;
        if (isOnline) {
            redisTemplate.opsForValue().set(key, "ONLINE");
        } else {
            redisTemplate.delete(key);
        }
    }

    public boolean getDvrStatus(String dvrUlid) {
        String key = "dvr:status:" + dvrUlid;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
}
