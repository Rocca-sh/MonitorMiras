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
        String streamId = (String) payload.get("stream");

        System.out.println("ZLM WEBHOOK: Ya no hay espectadores para el stream " + streamId);

        Map<String, Object> response = new HashMap<>();
        response.put("code", 0);

        // Buscar si tenemos la conexión (Dialog) viva hacia esta cámara
        Dialog dialog = ZlmController.activeDialogs.remove(streamId);

        if (dialog != null) {
            try {
                Request byeRequest = dialog.createRequest(Request.BYE);
                SipProvider sipProvider = sipConfig.getSipProviderUdp();
                ClientTransaction ct = sipProvider.getNewClientTransaction(byeRequest);
                dialog.sendRequest(ct);
                System.out.println("===== BYE ENVIADO AL DVR (sin espectadores) =====");
            } catch (Exception e) {
                System.err.println("Error al enviar BYE al DVR: " + e.getMessage());
            }
            // Si teníamos sesión activa y nadie está viendo → cerramos el stream en ZLM
            response.put("close", true);
        } else {
            // No había sesión SIP activa → dejamos el puerto abierto por si alguien se reconecta
            response.put("close", false);
        }

        return response;
    }
}
