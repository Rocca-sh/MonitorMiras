package miras.monitor.Dvr.Controller;

import miras.monitor.Dvr.Controller.Dto.DvrCreateDto;
import miras.monitor.Dvr.Model.Dvr;
import miras.monitor.Dvr.Service.DvrServ;
import miras.monitor.User.Model.UserPrincipal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dvr")
public class DvrController {

    private final DvrServ dvrServ;

    @Autowired
    public DvrController(DvrServ dvrServ) {
        this.dvrServ = dvrServ;
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerDevice(@RequestBody DvrCreateDto dto, @AuthenticationPrincipal UserPrincipal principal) {
        Dvr dvr = dto.DtoToModel();
        Dvr saved = dvrServ.registerDevice(dvr, principal.getOrgId(), principal.getUserId());
        
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/settings/{ulid}")
    public ResponseEntity<?> updateSettings(@PathVariable String ulid, @RequestBody DvrCreateDto dto, @AuthenticationPrincipal UserPrincipal principal) {
        Dvr updated = dvrServ.updateSettings(ulid, dto.DtoToModel(), principal.getOrgId());
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/remove/{ulid}")
    public ResponseEntity<?> removeDevice(@PathVariable String ulid, @AuthenticationPrincipal UserPrincipal principal) {
        dvrServ.removeDevice(ulid, principal.getOrgId());
        return ResponseEntity.ok().build();
    }



    @GetMapping(value = "/play/{sipId}")
    public ResponseEntity<?> viewStream(@PathVariable String sipId, @RequestParam(required = true) String channelId, @RequestParam(defaultValue = "0") int quality, @AuthenticationPrincipal UserPrincipal principal) {
        quality = 1;
        Map<String, String> links = dvrServ.playVideo(sipId, channelId, principal.getOrgId(), quality);
        return ResponseEntity.ok(links);
    }

    @PostMapping("/stop/{channelId}")
    public ResponseEntity<?> stopStream(@PathVariable String channelId, @AuthenticationPrincipal UserPrincipal principal) {
        dvrServ.stopVideo(channelId, principal.getOrgId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/clean/reboot/{sipId}")
    public ResponseEntity<?> restartDvr(@PathVariable String sipId, @AuthenticationPrincipal UserPrincipal principal) {
        dvrServ.cleanAndRebootDvr(sipId, principal.getOrgId());
        return ResponseEntity.ok(Map.of("message", "Comando de reinicio fisico enviado al DVR y registros limpiados"));
    }

    @PostMapping("/clean/{sipId}")
    public ResponseEntity<?> cleanDvr(@PathVariable String sipId, @AuthenticationPrincipal UserPrincipal principal) {
        dvrServ.cleanDvr(sipId, principal.getOrgId());
        return ResponseEntity.ok(Map.of("message", "Registros limpiados exitosamente. El DVR volvera a conectarse en el proximo latido."));
    }

    @GetMapping("/list")
    public ResponseEntity<?> listDevices(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(dvrServ.listDevices(principal.getOrgId()));
    }
    
    @GetMapping(value = "/channels/{sipId}", produces = "application/json")
    public ResponseEntity<?> getChannels(@PathVariable String sipId, @AuthenticationPrincipal UserPrincipal principal) {
        String jsonResult = dvrServ.getDvrChannels(sipId, principal.getOrgId());
        return ResponseEntity.ok(jsonResult);
    }
}
