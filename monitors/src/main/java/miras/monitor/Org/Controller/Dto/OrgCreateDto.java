package miras.monitor.Org.Controller.Dto;

import lombok.Data;

import miras.monitor.Org.Model.Org;

@Data
public class OrgCreateDto {
    private String name;
    private String email;
    private String passwd;

    public Org DtoToModel() {
        Org org = new Org();
        org.setName(this.name);
        org.setEmail(this.email);
        org.setPasswd(this.passwd);
        return org;
    }
}
