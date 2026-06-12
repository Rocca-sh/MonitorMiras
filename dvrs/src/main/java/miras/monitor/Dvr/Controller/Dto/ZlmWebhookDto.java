package miras.monitor.Dvr.Controller.Dto;

import lombok.Data;

@Data
public class ZlmWebhookDto {
    private String app;
    private String stream;
    private Boolean regist;
}
