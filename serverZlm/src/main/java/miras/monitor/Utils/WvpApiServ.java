package miras.monitor.Utils;

import org.springframework.stereotype.Service;

@Service
public class WvpApiServ {
    
    private final WvpApiRepo wvpApiRepo;

    public WvpApiServ(WvpApiRepo wvpApiRepo) {
        this.wvpApiRepo = wvpApiRepo;
    }

    public String getStreamLinks(String deviceId, String channelId) {
        return wvpApiRepo.getPlayLinks(deviceId, channelId);
    }

    public String getDeviceChannels(String deviceId) {
        return wvpApiRepo.getDeviceChannels(deviceId);
    }
}
