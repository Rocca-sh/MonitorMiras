package miras.monitor.Jt808;

import io.github.hylexus.jt.jt808.support.annotation.handler.Jt808RequestHandler;
import io.github.hylexus.jt.jt808.support.annotation.handler.Jt808RequestHandlerMapping;
import io.github.hylexus.jt.jt808.spec.Jt808Request;
import miras.monitor.Utils.RedisDvrService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.netty.buffer.ByteBuf;

@Component
@Jt808RequestHandler
public class LocationMsgHandler {

    private static final Logger log = LoggerFactory.getLogger(LocationMsgHandler.class);

    @Autowired
    private RedisDvrService redisDvrService;

    @Jt808RequestHandlerMapping(msgType = 0x0200)
    public void processLocationMsg(Jt808Request request) {
        String terminalId = request.header().terminalId();
        // El protocolo JT808-2019 rellena con ceros a la izquierda hasta 20 digitos.
        // Quitamos los ceros iniciales y le agregamos 0030 para que coincida exactamente con el SIP ID del DVR.
        terminalId = terminalId.replaceFirst("^0+(?!$)", "") + "0030";
        
        // El cuerpo del mensaje 0x0200
        ByteBuf body = request.body();
        
        // Parseo manual del JT/T808 0x0200 (Estandar)
        // 0-3: Alarma (DWORD)
        int alarm = body.readInt();
        // 4-7: Estado (DWORD)
        int status = body.readInt();
        // 8-11: Latitud (DWORD, multiplicada por 10^6)
        long latRaw = body.readUnsignedInt();
        double latitude = latRaw / 1000000.0;
        // 12-15: Longitud (DWORD, multiplicada por 10^6)
        long lonRaw = body.readUnsignedInt();
        double longitude = lonRaw / 1000000.0;
        // 16-17: Altitud (WORD)
        int altitude = body.readUnsignedShort();
        // 18-19: Velocidad (WORD, 1/10 km/h)
        int speedRaw = body.readUnsignedShort();
        double speed = speedRaw / 10.0;
        // 20-21: Direccion (WORD)
        int direction = body.readUnsignedShort();
        // 22-27: Tiempo BCD (YYMMDDhhmmss)
        // Omitimos el parseo del tiempo BCD 

        log.info("GPS DVR {}: Lat={}, Lon={}, Speed={} km/h", terminalId, latitude, longitude, speed);

        // Guardar la ubicacion en Redis para el frontend
        // La notificacion SSE se disparara automaticamente a traves de RedisEventListener
        redisDvrService.saveLocation(terminalId, latitude, longitude, speed);
    }
}
