package miras.monitor.Zlmedia.Controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sip.ClientTransaction;
import javax.sip.Dialog;
import javax.sip.SipProvider;
import javax.sip.message.Request;
import miras.monitor.Zlmedia.Config.SipConfig;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/hook")
public class ZlmWebHController {

    @Autowired
    private SipConfig sipConfig;

    @PostMapping("/on_stream_none_reader")
    public Map<String, Object> onStreamNoneReader(@RequestBody Map<String, Object> payload) {
        String streamId = (String) payload.get("stream"); // Ej: 44010200491310000031
        
        System.out.println("ZLM WEBHOOK: Ya no hay espectadores para el stream " + streamId);

        // Buscar si tenemos la conexión (Dialog) viva hacia esta cámara
        Dialog dialog = ZlmController.activeDialogs.remove(streamId);
        
        if (dialog != null) {
            try {
                // Crear el mensaje SIP BYE para decirle a la cámara que deje de mandar video
                Request byeRequest = dialog.createRequest(Request.BYE);
                SipProvider sipProvider = sipConfig.getSipProviderUdp();
                ClientTransaction ct = sipProvider.getNewClientTransaction(byeRequest);
                dialog.sendRequest(ct);
                
                System.out.println("===== BYE ENVIADO AL DVR (Cortando flujo de datos) =====");
            } catch (Exception e) {
                System.err.println("Error al enviar el BYE al DVR: ");
            }
        }

        // Le respondemos a ZLMediaKit diciéndole "OK, puedes cerrar el puerto y matar el stream"
        Map<String, Object> response = new HashMap<>();
        response.put("code", 0);
        response.put("close", true);
        return response;
    }
}
