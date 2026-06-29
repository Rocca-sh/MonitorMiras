package miras.monitor.Utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import miras.monitor.Dvr.Controller.SseDvrController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Service;
import java.util.Set;

@Service
public class RedisEventListener implements MessageListener {

    private static final Logger logger = LoggerFactory.getLogger(RedisEventListener.class);

    @Autowired
    private SseDvrController sseDvrController;

    @Autowired
    private RedisDvrService redisDvrService;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String eventType = new String(message.getChannel()); // Ej: __keyevent@1__:set o __keyevent@1__:del
        String redisKey = new String(message.getBody());     // Ej: dvr:44010200491110000030

        if (redisKey.startsWith("dvr:")) {
            String sipId = redisKey.substring(4);
            String orgId = redisDvrService.getOrgIdBySipId(sipId);
        
            Set<String> onlineDvrs = redisDvrService.getOnlineDvrsByOrg(orgId);
            sseDvrController.notifyClients(orgId, onlineDvrs);
        } else if (redisKey.startsWith("dvr_gps:")) {
            // El evento SET solo envia la llave, asi que consultamos Redis para obtener el JSON completo
            if (eventType.endsWith("set")) {
                String sipId = redisKey.substring(8);
                try {
                    String jsonLocation = redisDvrService.getLocation(sipId);
                    if (jsonLocation != null) {
                        String orgId = redisDvrService.getOrgIdBySipId(sipId);
                        sseDvrController.notifyGpsUpdate(orgId, sipId, jsonLocation);
                    }
                } catch (Exception e) {
                    logger.error("Error procesando actualizacion GPS en SSE: {}");
                }
            }
        }
    }
}
