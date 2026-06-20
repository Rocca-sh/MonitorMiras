package miras.monitor.User.Model.Repo;

import miras.monitor.User.Model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserPg extends JpaRepository<User, String> {
    Optional<User> findByEmail(String email);
}
