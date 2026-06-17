package miras.monitor.Dvr.Controller;

import miras.monitor.Dvr.Controller.Dto.ZlmWebhookDto;
import miras.monitor.Dvr.Model.Repo.DvrPg;
import miras.monitor.Utils.RedisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;


//TODO
@RestController
@RequestMapping("/api/webhook/zlm")
public class WebhookController {

    private final DvrPg dvrPg;
    private final RedisService redisService;

    @Autowired
    public WebhookController(DvrPg dvrPg, RedisService redisService) {
        this.dvrPg = dvrPg;
        this.redisService = redisService;
    }

    @PostMapping("/on_publish")
    public ResponseEntity<?> onPublish(@RequestBody ZlmWebhookDto payload) {
        String streamId = payload.getStream(); 
        
        boolean exists = dvrPg.findById(streamId).isPresent() || dvrPg.findBySipId(streamId).isPresent();
        
        Map<String, Object> response = new HashMap<>();
        if (exists) {
            response.put("code", 0);
            response.put("msg", "success");
        } else {
            response.put("code", 401);
            response.put("msg", "Unauthorized: DVR no encontrado");
        }
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/on_stream_changed")
    public ResponseEntity<?> onStreamChanged(@RequestBody ZlmWebhookDto payload) {
        String streamId = payload.getStream();
        Boolean isOnline = payload.getRegist();
        
        if (isOnline != null) {
            redisService.setDvrStatus(streamId, isOnline);
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("code", 0);
        response.put("msg", "success");
        return ResponseEntity.ok(response);
    }
}
