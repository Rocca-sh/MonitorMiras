package miras.monitor.Dvr.Service;

import miras.monitor.Dvr.Model.Dvr;
import java.util.List;
import java.util.Map;

public interface DvrServ {
    Dvr registerDevice(Dvr dvr, String orgUlid, String userUlid);
    Dvr updateSettings(String ulid, Dvr updatedData, String orgUlid);
    void removeDevice(String ulid, String orgUlid);
    String getDvrChannels(String sipId, String orgUlid);
    Map<String, String> playVideo(String dvrSipId, String channelSipId, String orgUlid);
    Map<String, Map<String, String>> playVideoBatch(String dvrSipId, List<String> channelIds, String orgUlid);
    void stopVideo(String channelSipId, String orgUlid);
    List<Map<String, Object>> listDevices(String orgSipId);
}
