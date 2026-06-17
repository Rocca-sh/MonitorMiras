package miras.monitor.Utils;

import org.springframework.stereotype.Service;

@Service
public class WvpApiServ {
    
    private final WvpApiRepo wvpApiRepo;

    public WvpApiServ(WvpApiRepo wvpApiRepo) {
        this.wvpApiRepo = wvpApiRepo;
    }

    public String getStreamLinks(String sipId) {
        return wvpApiRepo.getPlayLinks(sipId);
    }
}
