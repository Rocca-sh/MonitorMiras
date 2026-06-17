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

@RestController
@RequestMapping("/api/dvr")
@CrossOrigin(origins = "*")
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

    // Retorna un String porque Spring convertirá automáticamente a JSON el texto crudo si configuramos los headers
    @GetMapping(value = "/play/{sipId}", produces = "application/json")
    public ResponseEntity<?> viewStream(@PathVariable String sipId, @AuthenticationPrincipal UserPrincipal principal) {
        // En una app final aquí verificaríamos que el sipId pertenece al orgId del token
        String jsonResult = wvpApiServ.getStreamLinks(sipId);
        return ResponseEntity.ok(jsonResult);
    }

    @GetMapping("/list")
    public ResponseEntity<?> listDevices(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(dvrServ.listDevices(principal.getOrgId()));
    }
    
 
}
