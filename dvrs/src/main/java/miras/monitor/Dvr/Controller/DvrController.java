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

    @GetMapping("/view/{ulid}")
    public ResponseEntity<?> viewStream(@PathVariable String ulid, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok("Stream URL mock para el DVR: " + ulid);
    }

    @GetMapping("/list")
    public ResponseEntity<?> listDevices(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(dvrServ.listDevices(principal.getOrgId()));
    }
    
 
}
