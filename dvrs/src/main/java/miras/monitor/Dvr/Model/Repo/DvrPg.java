package miras.monitor.Dvr.Model.Repo;

import miras.monitor.Dvr.Model.Dvr;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DvrPg extends JpaRepository<Dvr, String> {
    List<Dvr> findByOrganizationUlid(String orgUlid);
    Optional<Dvr> findBySipId(String sipId);
}
