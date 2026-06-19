package miras.monitor.User.Controller.Dto;

import lombok.Data;

import miras.monitor.User.Model.User;
import miras.monitor.Org.Model.Org;

@Data
public class LinkUserOrgDto {
    private String email;
    private String ulidOrg;
    private String role;
    private String orgPassword;
    private String code;

    public User DtoToModel() {
        User user = new User();
        user.setEmail(this.email);
        user.setRole(this.role);
        
        Org org = new Org();
        org.setUlid(this.ulidOrg);
        org.setPasswd(this.orgPassword);
        user.setOrganization(org);
        
        return user;
    }
}
