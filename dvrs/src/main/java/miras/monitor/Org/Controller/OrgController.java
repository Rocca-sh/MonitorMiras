package miras.monitor.Org.Controller;

import miras.monitor.Org.Controller.Dto.OrgCreateDto;
import miras.monitor.Org.Controller.Dto.OrgCodeGenDto;
import miras.monitor.Org.Model.Org;
import miras.monitor.Org.Service.OrgServ;
import miras.monitor.Utils.RedisService;
import miras.monitor.Utils.EmailServ;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/org")
public class OrgController {

    private final OrgServ orgServ;
    private final RedisService redisService;
    private final EmailServ emailServ;

    @Autowired
    public OrgController(OrgServ orgServ, RedisService redisService, @Autowired(required = false) EmailServ emailServ) {
        this.orgServ = orgServ;
        this.redisService = redisService;
        this.emailServ = emailServ;
    }

    @PostMapping("/create")
    public ResponseEntity<?> createOrg(@RequestBody OrgCreateDto dto) {
        Org org = dto.DtoToModel();
        Org savedOrg = orgServ.createOrg(org);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedOrg.getUlid());
    }

    @PostMapping("/code/generate")
    public ResponseEntity<?> orgCodeGen(@RequestBody OrgCodeGenDto dto) {
        // Valida que la org exista
        Org org = orgServ.getOrgByUlid(dto.getUlidOrg());

        // Genera el código en Redis
        String code = redisService.genCode(org.getEmail());

        // Envía por email
        if (emailServ != null) {
            emailServ.sendCode(org.getEmail(), code);
        }

        return ResponseEntity.ok().build();
    }
}
