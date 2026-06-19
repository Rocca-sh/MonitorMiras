package miras.monitor.Dvr.Controller.Dto;

import lombok.Data;
import miras.monitor.Dvr.Model.Dvr;

@Data
public class DvrCreateDto {
    private String name;
    private String protocol;
    private String sipId;

    public Dvr DtoToModel() {
        Dvr dvr = new Dvr();
        dvr.setName(this.name);
        dvr.setProtocol(this.protocol);
        dvr.setSipId(this.sipId);
        return dvr;
    }
}
