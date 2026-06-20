package miras.monitor.Dvr.Controller.Dto;

import java.util.List;

@Data
public class PlayBatchDto {
    private String dvrSipId;
    private List<String> channelIds;

    public PlayBatchDto() {
    }

    public PlayBatchDto(String dvrSipId, List<String> channelIds) {
        this.dvrSipId = dvrSipId;
        this.channelIds = channelIds;
    }

}
