package miras.monitor.Org.Model.Repo;

import miras.monitor.Org.Model.Org;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrgPg extends JpaRepository<Org, String> {
}
