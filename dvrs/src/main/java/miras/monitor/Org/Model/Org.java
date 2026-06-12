package miras.monitor.Org.Model;

import jakarta.persistence.*;
import com.github.f4b6a3.ulid.UlidCreator;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import miras.monitor.User.Model.User;

@Entity
@Table(name = "organizations")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Org {

    @Id
    @Column(length = 26)
    private String ulid;

    private String name;
    private String email;
    private String passwd;

    @PrePersist
    public void prePersist() {
        if (this.ulid == null || this.ulid.isEmpty()) {
            this.ulid = UlidCreator.getUlid().toString();
        }
    }
}
