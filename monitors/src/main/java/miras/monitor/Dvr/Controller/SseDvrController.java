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
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import miras.monitor.Utils.RedisDvrService;

@RestController
@RequestMapping("/api/view/dvr-sse")
public class SseDvrController {

    // Mapa thread-safe para agrupar las conexiones abiertas por ID de Organización
    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    @Autowired
    private RedisDvrService redisDvrService;

    // 1. El cliente de Vite hace GET a esta ruta y se queda "enganchado"
    @GetMapping("/stream")
    public SseEmitter streamEvents(@AuthenticationPrincipal UserPrincipal principal) {
        // Obtenemos la empresa del usuario validado por JWT
        String orgId = principal.getOrgId();
        
        // 0L significa timeout infinito. La conexión no se corta.
        SseEmitter emitter = new SseEmitter(0L);

        // Metemos al cliente a la lista de su empresa
        emitters.computeIfAbsent(orgId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        // Reglas de limpieza si el cliente cierra la pestaña o se va el internet
        emitter.onCompletion(() -> removeEmitter(orgId, emitter));
        emitter.onTimeout(() -> removeEmitter(orgId, emitter));
        emitter.onError((e) -> removeEmitter(orgId, emitter));

        try {
            // Un mensaje de bienvenida
            emitter.send(SseEmitter.event().name("init").data("Conectado al feed en vivo"));
            
            // Enviamos la lista inicial inmediatamente sin esperar eventos
            Set<String> onlineDvrs = redisDvrService.getOnlineDvrsByOrg(orgId);
            emitter.send(SseEmitter.event().name("dvr-update").data(onlineDvrs));
            
        } catch (IOException e) {
            emitter.completeWithError(e);
        }

        return emitter;
    }

    // 2. Solo recibe la lista pura y se la pasa a los clientes
    public void notifyClients(String orgId, Set<String> onlineDvrs) {
        List<SseEmitter> orgClients = emitters.get(orgId);
        
        if (orgClients != null) {
            for (SseEmitter client : orgClients) {
                try {
                    // Manda la lista como JSON (Spring Boot convierte el Set a JSON automáticamente)
                    client.send(SseEmitter.event().name("dvr-update").data(onlineDvrs));
                } catch (IOException e) {
                    client.completeWithError(e);
                    removeEmitter(orgId, client);
                }
            }
        }
    }

    // Método auxiliar de limpieza de memoria
    private void removeEmitter(String orgId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(orgId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                emitters.remove(orgId);
            }
        }
    }
}
