package miras.monitor.Zlmedia.Controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import miras.monitor.Zlmedia.Config.SipConfig;

import java.util.HashMap;
import java.util.Map;
import miras.monitor.Zlmedia.Service.ZlmVideoServ;

@RestController
@RequestMapping("/api/hook")
public class ZlmWebHController {
    
    @Autowired
    private ZlmVideoServ zlmVideoServ;

    @Autowired
    private SipConfig sipConfig;

    @PostMapping("/on_stream_none_reader")
    public Map<String, Object> onStreamNoneReader(@RequestBody Map<String, Object> payload) {
        String streamId = (String) payload.get("stream");

        System.out.println("ZLM WEBHOOK: Ya no hay espectadores para el stream " + streamId);

        Map<String, Object> response = new HashMap<>();
        response.put("code", 0);

        // Manda cerrar el stream internamente, que procesara la cola de BYEs al DVR
        zlmVideoServ.closeStream(streamId, "WEBHOOK_ZLM");

        response.put("close", true);

        return response;
    }
}
