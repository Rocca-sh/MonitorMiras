package miras.monitor.Zlmedia.Service;

import java.util.Map;

public interface ZlmVideoServ {
    Map<String, String> getPlaybackLinks(String channelSipId, String dvrIp, int dvrPort, int quality);
    String getCatalogWithTimeout(String dvrSipId, String dvrIp, int dvrPort);
    void stopStream(String streamId);
    void closeStream(String streamId, String source);
    void rebootDvr(String dvrSipId, String dvrIp, int dvrPort);
}
