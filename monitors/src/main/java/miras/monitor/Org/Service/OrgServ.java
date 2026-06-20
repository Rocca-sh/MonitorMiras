package miras.monitor.Org.Service;

import miras.monitor.Org.Model.Org;

public interface OrgServ {
    Org createOrg(Org org);
    Org getOrgBySipId(String sipId);
}
