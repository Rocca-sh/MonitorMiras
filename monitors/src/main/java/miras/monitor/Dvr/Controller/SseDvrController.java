package miras.monitor.Dvr.Controller;

import miras.monitor.User.Model.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.Set;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import miras.monitor.Utils.RedisDvrService;

@RestController
@RequestMapping("/api/view/dvr-sse")
public class SseDvrController {

    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    
    private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> heartbeatTask;

    @Autowired
    private RedisDvrService redisDvrService;

    @GetMapping("/stream")
    public SseEmitter streamEvents(@AuthenticationPrincipal UserPrincipal principal) {
        String orgId = principal.getOrgId();
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        emitters.computeIfAbsent(orgId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> removeEmitter(orgId, emitter));
        emitter.onTimeout(() -> removeEmitter(orgId, emitter));
        emitter.onError((e) -> removeEmitter(orgId, emitter));

        try {
            emitter.send(SseEmitter.event().name("init").data("Conectado al feed en vivo"));

            Set<String> onlineDvrs = redisDvrService.getOnlineDvrsByOrg(orgId);
            emitter.send(SseEmitter.event().name("dvr-update").data(onlineDvrs));

        } catch (IOException e) {
            emitter.completeWithError(e);
        }

        return emitter;
    }

    public void notifyClients(String orgId, Set<String> onlineDvrs) {
        List<SseEmitter> orgClients = emitters.get(orgId);

        if (orgClients != null) {
            for (SseEmitter client : orgClients) {
                try {
                    client.send(SseEmitter.event().name("dvr-update").data(onlineDvrs));
                } catch (IOException e) {
                    client.completeWithError(e);
                    removeEmitter(orgId, client);
                }
            }
        }
    }

    public void notifyGpsUpdate(String orgId, String sipId, String jsonLocation) {
        List<SseEmitter> orgClients = emitters.get(orgId);

        if (orgClients != null) {
            String payload = String.format("{\"sipId\":\"%s\", \"location\":%s}", sipId, jsonLocation);
            for (SseEmitter client : orgClients) {
                try {
                    client.send(SseEmitter.event().name("gps-update").data(payload));
                } catch (IOException e) {
                    client.completeWithError(e);
                    removeEmitter(orgId, client);
                }
            }
        }
    }

    private void removeEmitter(String orgId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(orgId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                emitters.remove(orgId);
            }
        }
    }

    public void startHeartbeat() {
        heartbeatTask = heartbeatExecutor.scheduleAtFixedRate(() -> {
            for (Map.Entry<String, List<SseEmitter>> entry : emitters.entrySet()) {
                String orgId = entry.getKey();
                for (SseEmitter client : entry.getValue()) {
                    try {
                        client.send(SseEmitter.event().comment("ping"));
                    } catch (IOException e) {
                        client.completeWithError(e);
                        removeEmitter(orgId, client);
                    }
                }
            }
        }, 15, 15, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void stopHeartbeat() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel(true);
        }
        heartbeatExecutor.shutdown();
    }
}