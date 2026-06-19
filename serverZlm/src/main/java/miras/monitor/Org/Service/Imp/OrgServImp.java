package miras.monitor.Org.Service.Imp;

import miras.monitor.Org.Model.Org;
import miras.monitor.Org.Model.Repo.OrgPg;
import miras.monitor.Org.Service.OrgServ;
import miras.monitor.Exceptions.NotFound.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import miras.monitor.lib.SipCreator;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Service
public class OrgServImp implements OrgServ {

    private final OrgPg orgPg;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public OrgServImp(OrgPg orgPg) {
        this.orgPg = orgPg;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    @Override
    public Org createOrg(Org org) {
        long nextId = orgPg.count() + 1;
        org.setUlid(SipCreator.genOrgSip(nextId));
        org.setPasswd(passwordEncoder.encode(org.getPasswd()));
        return orgPg.save(org);
    }

    @Override
    public Org getOrgByUlid(String ulid) {
        return orgPg.findById(ulid)
                .orElseThrow(() -> new NotFoundException("Organización no encontrada"));
    }
}
