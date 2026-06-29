package miras.monitor.Org.Controller;

import miras.monitor.Org.Controller.Dto.OrgCreateDto;
import miras.monitor.Org.Controller.Dto.OrgCodeGenDto;
import miras.monitor.Org.Model.Org;
import miras.monitor.Org.Service.OrgServ;
import miras.monitor.Org.Model.Repo.OrgPg;
import miras.monitor.Utils.RedisService;
import miras.monitor.Utils.EmailServ;
import miras.monitor.User.Model.Repo.UserPg;
import miras.monitor.User.Model.User;
import miras.monitor.Jwt.Service.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/org")
@CrossOrigin(origins = "*")
public class OrgController {

    private final OrgServ orgServ;
    private final RedisService redisService;
    private final EmailServ emailServ;
    private final OrgPg orgPg; // Para pruebas
    private final UserPg userPg;
    private final JwtService jwtService;

    @Autowired
    public OrgController(OrgServ orgServ, RedisService redisService, @Autowired(required = false) EmailServ emailServ, OrgPg orgPg, UserPg userPg, JwtService jwtService) {
        this.orgServ = orgServ;
        this.redisService = redisService;
        this.emailServ = emailServ;
        this.orgPg = orgPg;
        this.userPg = userPg;
        this.jwtService = jwtService;
    }

    @PostMapping("/create")
    public ResponseEntity<?> createOrg(@RequestBody OrgCreateDto dto) {
        Org org = dto.DtoToModel();
        Org savedOrg = orgServ.createOrg(org);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedOrg.getSipId());
    }

    @PostMapping("/code/generate")
    public ResponseEntity<?> orgCodeGen(@RequestBody OrgCodeGenDto dto) {
        // Valida que la org exista
        Org org = orgServ.getOrgBySipId(dto.getSipIdOrg());

        // Genera el codigo en Redis
        String code = redisService.genCode(org.getEmail());

        // Envia por email
        if (emailServ != null) {
            emailServ.sendCode(org.getEmail(), code);
        }

        return ResponseEntity.ok().build();
    }

    //+++test+++
    @GetMapping("/test/seed")
    public ResponseEntity<?> seedTestOrg() {
        Org org = orgPg.findById("4401020049").orElse(null);
        if (org == null) {
            org = new Org();
            org.setSipId("4401020049");
            org.setName("Empresa de Prueba");
            org.setEmail("test@empresa.com");
            BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
            org.setPasswd(encoder.encode("root"));
            org = orgPg.save(org);
        }

        User user = userPg.findByEmail("test@empresa.com").orElse(null);
        if (user == null) {
            user = new User();
            user.setName("Usuario Prueba");
            user.setEmail("test@empresa.com");
            BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
            user.setPasswd(encoder.encode("root"));
            user.setRole("OWNER");
            user.setOrganization(org);
            user = userPg.save(user);
        }

        String token = jwtService.createToken(user.getUlid(), org.getSipId(), user.getRole());

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Empresa y Usuario de prueba listos");
        response.put("orgSipId", org.getSipId());
        response.put("email", user.getEmail());
        response.put("password", "root");
        response.put("token", token);

        return ResponseEntity.ok(response);
    }
}
