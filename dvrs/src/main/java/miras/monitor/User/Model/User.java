package miras.monitor.User.Model;

import jakarta.persistence.*;
import com.github.f4b6a3.ulid.UlidCreator;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import miras.monitor.Org.Model.Org;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @Column(length = 26)
    private String ulid;

    private String name;
    private String email;
    private String phoneNumber;
    private String passwd;
    private String sessionToken;
    private String role; // Ej: "MEMBER", "VIEWER", "OWNER", "ADMIN"

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "org_ulid")
    private Org organization;

    @PrePersist
    public void prePersist() {
        if (this.ulid == null || this.ulid.isEmpty()) {
            this.ulid = UlidCreator.getUlid().toString();
        }
    }
}
