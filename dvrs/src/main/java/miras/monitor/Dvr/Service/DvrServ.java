package miras.monitor.Dvr.Service;

import miras.monitor.Dvr.Model.Dvr;
import java.util.List;

public interface DvrServ {
    Dvr registerDevice(Dvr dvr, String orgUlid, String userUlid);
    Dvr updateSettings(String ulid, Dvr updatedData, String orgUlid);
    void removeDevice(String ulid, String orgUlid);
    List<Dvr> listDevices(String orgUlid);
}
