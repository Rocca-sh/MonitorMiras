package miras.monitor.User.Controller;

import miras.monitor.Jwt.Service.JwtService;
import miras.monitor.User.Controller.Dto.LoginDto;
import miras.monitor.User.Controller.Dto.LoginCodeGenDto;
import miras.monitor.User.Controller.Dto.LoginCodeVerifyDto;
import miras.monitor.User.Controller.Dto.UserCreateDto;
import miras.monitor.User.Controller.Dto.LinkUserOrgDto;
import miras.monitor.User.Model.User;
import miras.monitor.User.Service.UserAuthServ;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import miras.monitor.Utils.RedisService;
import miras.monitor.Utils.EmailServ;
import miras.monitor.Exceptions.UnAuthorized.UnauthorizedException;

@RestController
@RequestMapping("/auth/users")
public class UserAuthController {

    private final UserAuthServ userAuthServ;
    private final JwtService jwtService;
    private final RedisService redisService;
    private final EmailServ emailServ;

    @Autowired
    public UserAuthController(UserAuthServ userAuthServ, JwtService jwtService, RedisService redisService, @Autowired(required = false) EmailServ emailServ) {
        this.userAuthServ = userAuthServ;
        this.jwtService = jwtService;
        this.redisService = redisService;
        this.emailServ = emailServ;
    }

    private String genSession(User user) {
        String role = user.getRole() != null ? user.getRole() : "VIEWER";
        String orgId = user.getOrganization() != null ? user.getOrganization().getUlid() : null;
        return jwtService.createToken(user.getUlid(), orgId, role);
    }

    @PostMapping
    public ResponseEntity<?> createAccount(@RequestBody UserCreateDto dto) {
        User user = dto.DtoToModel();
        User createdUser = userAuthServ.createUser(user);
        String token = genSession(createdUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(token);
    }

    @PostMapping("/login/password")
    public ResponseEntity<?> loginPsswd(@RequestBody LoginDto dto) {
        User user = userAuthServ.loginPsswd(dto.getEmail(), dto.getPsswd());
        String token = genSession(user);
        return ResponseEntity.ok(token);
    }

    @PostMapping("/login/code/generate")
    public ResponseEntity<?> loginCodeGen(@RequestBody LoginCodeGenDto dto) {
        // Valida que el usuario exista
        User user = userAuthServ.getUserByEmail(dto.getEmail());

        // Generar codigo en el servicio Redis
        String code = redisService.genCode(user.getEmail());
        
        // Enviar usando EmailServ si existe una implementación
        if (emailServ != null) {
            emailServ.sendCode(user.getEmail(), code);
        }
        
        return ResponseEntity.ok().build();
    }

    @PostMapping("/login/code/verify")
    public ResponseEntity<?> loginCodeVerify(@RequestBody LoginCodeVerifyDto dto) {
        if (!redisService.verifyCode(dto.getKey(), dto.getPsswd())) {
            throw new UnauthorizedException("Codigo invalido o expirado");
        }
        User user = userAuthServ.getUserByEmail(dto.getKey());
        String token = genSession(user);
        return ResponseEntity.ok(token);
    }

    @PostMapping("/logout/{ulid}")
    public ResponseEntity<?> logout(@PathVariable String ulid) {
        // Logica de logout, si es necesario invalidar token en Redis
        return ResponseEntity.ok().build();
    }

    @PostMapping("/link-org")
    public ResponseEntity<?> linkUserToOrg(@RequestBody LinkUserOrgDto dto) {
        User model = dto.DtoToModel();
        User user = userAuthServ.linkUserToOrg(model, dto.getCode());
        String token = genSession(user);
        return ResponseEntity.ok(token);
    }
}
