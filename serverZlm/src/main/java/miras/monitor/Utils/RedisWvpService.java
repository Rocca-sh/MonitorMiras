package miras.monitor.Utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import miras.monitor.Dvr.Controller.SseWvpController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Service;
import java.util.Set;

@Service
public class RedisWvpService implements MessageListener {

    private static final Logger logger = LoggerFactory.getLogger(RedisWvpService.class);

    @Autowired
    private SseWvpController sseWvpController;

    // Inyectamos tu servicio de memoria (Caché + Lista de Encendidos)
    @Autowired
    private RedisDvrService redisDvrService;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String eventType = new String(message.getChannel()); // Ej: __keyevent@0__:set o __keyevent@0__:del
        String redisKey = new String(message.getBody());     // Ej: VVSK_WVP_DEVICE_ONLINE_3402...

        if (redisKey.contains("WVP_DEVICE_ONLINE_")) {
            
            // 1. Extraemos el SIP ID 
            String sipId = redisKey.substring(redisKey.lastIndexOf("_") + 1);
                logger.info("==========================================================");
                logger.info("FINGIENDO ENVÍO DE sipId: {}", sipId);
                logger.info("CÓDIGO DE ipId: {}", sipId);
                logger.info("==========================================================");
            System.out.println();
            
            //Buscamos a qué empresa pertenece (Si no existe, lanzará NotFoundException y cortará el flujo aquí)
            String orgId = redisDvrService.getOrgIdBySipId(sipId);
            
            // 3. Determinamos si se conectó (set) o se desconectó (del / expired)
            if (eventType.endsWith("set")) {
                redisDvrService.addDvrOnline(orgId, sipId);
            } else if (eventType.endsWith("del") || eventType.endsWith("expired")) {
                redisDvrService.removeDvrOnline(orgId, sipId);
            }

            // 4. Extraemos la lista actualizada y purificada
            Set<String> onlineDvrs = redisDvrService.getOnlineDvrsByOrg(orgId);

            // 5. Se la pasamos al controlador SSE
            sseWvpController.notifyClients(orgId, onlineDvrs);
        }
    }
}
