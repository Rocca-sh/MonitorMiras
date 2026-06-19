package miras.monitor.Dvr.Controller;
import miras.monitor.User.Model.UserPrincipal;
import miras.monitor.Utils.RedisDvrService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@RestController
@RequestMapping("/api/view/dvrs")
public class SseWebhookController {

    @Autowired
    private RedisDvrService redisDvrService;

    // Mapa para guardar los emisores por Organización: idOrgOwner -> Lista de SseEmitters
    private final Map<String, List<SseEmitter>> orgEmitters = new ConcurrentHashMap<>();

    // Endpoint para que el cliente se suscriba al stream
    @GetMapping("/stream")
    public SseEmitter streamDvrsByOrg(@AuthenticationPrincipal UserPrincipal principal) {
        String orgId = principal.getOrgId();
        
        // Creamos un emisor con timeout infinito (0L) o un valor grande si hay proxys cortando.
        SseEmitter emitter = new SseEmitter(0L);
        
        orgEmitters.computeIfAbsent(orgId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        // Callbacks para limpiar el mapa cuando el cliente se desconecte o haya error
        emitter.onCompletion(() -> removeEmitter(orgId, emitter));
        emitter.onTimeout(() -> removeEmitter(orgId, emitter));
        emitter.onError((e) -> removeEmitter(orgId, emitter));

        // ENVIAR ESTADO INICIAL: Tan pronto se conecta, le mandamos lo que hay en Redis
        try {
            Set<String> currentOnlineDvrs = redisDvrService.getOnlineDvrsByOrg(orgId);
            emitter.send(SseEmitter.event().name("dvr-update").data(currentOnlineDvrs));
        } catch (IOException e) {
            emitter.completeWithError(e);
            removeEmitter(orgId, emitter);
        }

        return emitter;
    }

    public void notifyOrganization(String orgId, Set<String> onlineDvrs) {
        List<SseEmitter> emitters = orgEmitters.get(orgId);
        if (emitters != null) {
            for (SseEmitter emitter : emitters) {
                try {
                    // Enviamos el evento con nombre "dvr-update" y la lista de IDs actualizada
                    emitter.send(SseEmitter.event().name("dvr-update").data(onlineDvrs));
                } catch (IOException e) {
                    emitter.completeWithError(e);
                    removeEmitter(orgId, emitter);
                }
            }
        }
    }

    private void removeEmitter(String orgId, SseEmitter emitter) {
        List<SseEmitter> emitters = orgEmitters.get(orgId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                orgEmitters.remove(orgId); // Limpiamos la lista si ya no hay clientes
            }
        }
    }
}
