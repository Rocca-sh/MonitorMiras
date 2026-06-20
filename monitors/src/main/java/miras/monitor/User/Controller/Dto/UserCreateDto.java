package miras.monitor.User.Controller.Dto;

import lombok.Data;
import miras.monitor.Org.Model.Org;
import miras.monitor.User.Model.User;

@Data
public class UserCreateDto {
    private String name;
    private String email;
    private String passwd;
    private String sipIdOrg;
    private String orgPassword;
    private String phoneNumber;

    public User DtoToModel() {
        User user = new User();
        user.setName(this.name);
        user.setEmail(this.email);
        user.setPasswd(this.passwd);
        user.setPhoneNumber(this.phoneNumber);
        
        if (this.sipIdOrg != null && !this.sipIdOrg.isEmpty()) {
            Org org = new Org();
            org.setSipId(this.sipIdOrg);
            org.setPasswd(this.orgPassword);
            user.setOrganization(org);
        }
        
        return user;
    }
}
