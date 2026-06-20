package miras.monitor.User.Controller.Dto;

import lombok.Data;

import miras.monitor.User.Model.User;
import miras.monitor.Org.Model.Org;

@Data
public class LinkUserOrgDto {
    private String email;
    private String sipIdOrg;
    private String role;
    private String orgPassword;
    private String code;

    public User DtoToModel() {
        User user = new User();
        user.setEmail(this.email);
        user.setRole(this.role);
        
        Org org = new Org();
        org.setSipId(this.sipIdOrg);
        org.setPasswd(this.orgPassword);
        user.setOrganization(org);
        
        return user;
    }
}
