package miras.monitor.User.Service.Imp;

import miras.monitor.User.Model.User;
import miras.monitor.User.Model.Repo.UserPg;
import miras.monitor.Org.Model.Repo.OrgPg;
import miras.monitor.User.Service.UserAuthServ;
import miras.monitor.Exceptions.UnAuthorized.UnauthorizedException;
import miras.monitor.Exceptions.Exist.ExistException;
import miras.monitor.Exceptions.NotFound.NotFoundException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import miras.monitor.Utils.RedisService;
import miras.monitor.Org.Model.Org;

@Service
public class UserAuthServImp implements UserAuthServ {

    private final UserPg userPg;
    private final OrgPg orgPg;
    private final PasswordEncoder passwordEncoder;
    private final RedisService redisService;

    @Autowired
    public UserAuthServImp(UserPg userPg, OrgPg orgPg, RedisService redisService) {
        this.userPg = userPg;
        this.orgPg = orgPg;
        this.redisService = redisService;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    @Override
    @Transactional
    public User createUser(User user) {
        if(userPg.findByEmail(user.getEmail()).isPresent()){
            throw new ExistException("El correo ya esta registrado");
        }

        if (user.getOrganization() != null && user.getOrganization().getSipId() != null) {
            Org org = orgPg.findById(user.getOrganization().getSipId())
                    .orElseThrow(() -> new NotFoundException("El ID de la empresa no existe"));
            
            if (!passwordEncoder.matches(user.getOrganization().getPasswd(), org.getPasswd())) {
                throw new UnauthorizedException("Contrasena de la empresa incorrecta");
            }
            user.setRole("OWNER");
        } else {
            user.setRole("VIEWER");
        }
        
        user.setPasswd(passwordEncoder.encode(user.getPasswd()));
        
        return userPg.save(user);
    }

    @Override
    public User loginPsswd(String email, String psswd) {
        User user = getUserByEmail(email);
        
        if (!passwordEncoder.matches(psswd, user.getPasswd())) {
            throw new UnauthorizedException("Email o contrasena invalidos");
        }
        
        return user;
    }

    @Override
    public User getUserByEmail(String email) {
        return userPg.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
    }

    @Override
    @Transactional
    public User linkUserToOrg(User linkData, String code) {
        User user = getUserByEmail(linkData.getEmail());
        Org org = orgPg.findById(linkData.getOrganization().getSipId()).orElseThrow(() -> new NotFoundException("Empresa no encontrada"));
        
        String role = linkData.getRole();
        if (!"MEMBER".equalsIgnoreCase(role) && !"VIEWER".equalsIgnoreCase(role)) {
            throw new UnauthorizedException("Rol invalido. Solo se permite MEMBER o VIEWER.");
        }
        
        if (!passwordEncoder.matches(linkData.getOrganization().getPasswd(), org.getPasswd())) {
            throw new UnauthorizedException("Contrasena de empresa incorrecta");
        }
        
        if ("MEMBER".equalsIgnoreCase(role)) {
            if (code == null || !redisService.verifyCode(org.getEmail(), code)) {
                throw new UnauthorizedException("Codigo de verificacion invalido o expirado");
            }
        }
        
        user.setOrganization(org);
        user.setRole(role.toUpperCase());
        return userPg.save(user);
    }
}
