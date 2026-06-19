package miras.monitor.Dvr.Controller;

import miras.monitor.Dvr.Controller.Dto.DvrCreateDto;
import miras.monitor.Dvr.Model.Dvr;
import miras.monitor.Dvr.Service.DvrServ;
import miras.monitor.User.Model.UserPrincipal;
import miras.monitor.Utils.RedisDvrService;
import miras.monitor.Utils.WvpApiServ;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dvr")
public class DvrController {

    private final DvrServ dvrServ;
    private final RedisDvrService redisDvrService;
    private final SseWvpController sseWvpController;
    private final WvpApiServ wvpApiServ;

    @Autowired
    public DvrController(DvrServ dvrServ, RedisDvrService redisDvrService, SseWvpController sseWvpController, WvpApiServ wvpApiServ) {
        this.dvrServ = dvrServ;
        this.redisDvrService = redisDvrService;
        this.sseWvpController = sseWvpController;
        this.wvpApiServ = wvpApiServ;
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerDevice(@RequestBody DvrCreateDto dto, @AuthenticationPrincipal UserPrincipal principal) {
        Dvr dvr = dto.DtoToModel();
        Dvr saved = dvrServ.registerDevice(dvr, principal.getOrgId(), principal.getUserId());
        
        // forzamos la sincronización para que se ponga online 
        boolean wasOnline = redisDvrService.checkAndSyncDvrStatus(saved.getSipId(), principal.getOrgId());
        
        if (wasOnline) {
            sseWvpController.notifyClients(principal.getOrgId(), redisDvrService.getOnlineDvrsByOrg(principal.getOrgId()));
        }

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
    public ResponseEntity<?> viewStream(@PathVariable String sipId, @RequestParam(required = true) String channelId, @AuthenticationPrincipal UserPrincipal principal) {
        List<String> links = dvrServ.playVideo(sipId, channelId, principal.getOrgId());
        return ResponseEntity.ok(links);
    }

    @GetMapping("/list")
    public ResponseEntity<?> listDevices(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(dvrServ.listDevices(principal.getOrgId()));
    }
    
    @GetMapping("/channels/{sipId}")
    public ResponseEntity<?> getChannels(@PathVariable String sipId, @AuthenticationPrincipal UserPrincipal principal) {
        String jsonResult = dvrServ.getDvrChannels(sipId, principal.getOrgId());
        return ResponseEntity.ok(jsonResult);
    }
}
