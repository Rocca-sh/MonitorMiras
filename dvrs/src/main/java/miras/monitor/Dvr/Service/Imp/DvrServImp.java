package miras.monitor.Dvr.Service.Imp;

import miras.monitor.Dvr.Model.Dvr;
import miras.monitor.Org.Model.Org;
import miras.monitor.Org.Model.Repo.OrgPg;
import miras.monitor.Dvr.Model.Repo.DvrPg;
import miras.monitor.Dvr.Service.DvrServ;
import miras.monitor.Exceptions.NotFound.NotFoundException;
import miras.monitor.Exceptions.UnAuthorized.UnauthorizedException;
import miras.monitor.User.Model.Repo.UserPg;
import miras.monitor.User.Model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;

@Service
public class DvrServImp implements DvrServ {

    private final DvrPg dvrPg;
    private final OrgPg orgPg;
    private final UserPg userPg;

    @Autowired
    public DvrServImp(DvrPg dvrPg, OrgPg orgPg, UserPg userPg) {
        this.dvrPg = dvrPg;
        this.orgPg = orgPg;
        this.userPg = userPg;
    }

    private Org getOrg(String orgUlid) {
        if (orgUlid == null || orgUlid.isEmpty()) {
            throw new UnauthorizedException("El usuario no pertenece a ninguna empresa");
        }
        return orgPg.findById(orgUlid).orElseThrow(() -> new NotFoundException("Empresa no encontrada"));
    }

    @Override
    @Transactional
    public Dvr registerDevice(Dvr dvr, String orgUlid, String userUlid) {
        if (orgUlid == null || orgUlid.isEmpty()) {
            throw new UnauthorizedException("El usuario no pertenece a ninguna empresa");
        }
        try {
            Org orgRef = orgPg.getReferenceById(orgUlid);
            User userRef = userPg.getReferenceById(userUlid);
            dvr.setOrganization(orgRef);
            dvr.setCreator(userRef);
            return dvrPg.save(dvr);
        } catch (Exception e) {
            throw new RuntimeException("No se pudo registrar el DVR: " + e.getMessage());
        }
    }

    @Override
    public Dvr updateSettings(String ulid, Dvr updatedData, String orgUlid) {
        Dvr dvr = dvrPg.findById(ulid).orElseThrow(() -> new NotFoundException("DVR no encontrado"));
        if (!dvr.getOrganization().getUlid().equals(orgUlid)) {
            throw new UnauthorizedException("No tienes permiso sobre este DVR");
        }
        
        dvr.setName(updatedData.getName());
        dvr.setProtocol(updatedData.getProtocol());
        dvr.setSipId(updatedData.getSipId());
        
        return dvrPg.save(dvr);
    }

    @Override
    public void removeDevice(String ulid, String orgUlid) {
        Dvr dvr = dvrPg.findById(ulid).orElseThrow(() -> new NotFoundException("DVR no encontrado"));
        if (!dvr.getOrganization().getUlid().equals(orgUlid)) {
            throw new UnauthorizedException("No tienes permiso sobre este DVR");
        }
        dvrPg.delete(dvr);
    }

    @Override
    public List<Dvr> listDevices(String orgUlid) {
        if (orgUlid == null || orgUlid.isEmpty()) return List.of();
        return dvrPg.findByOrganizationUlid(orgUlid);
    }
}
