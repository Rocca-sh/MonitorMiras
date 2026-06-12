package miras.monitor.Dvr.Model;

import jakarta.persistence.*;
import com.github.f4b6a3.ulid.UlidCreator;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import miras.monitor.Org.Model.Org;
import miras.monitor.User.Model.User;

@Entity
@Table(name = "dvrs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Dvr {

    @Id
    @Column(length = 26)
    private String ulid;

    private String name;
    private String protocol;
    
    @Column(name = "sip_id", length = 30)
    private String sipId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "org_ulid", nullable = false)
    private Org organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_ulid")
    private User creator;

    @PrePersist
    public void prePersist() {
        if (this.ulid == null || this.ulid.isEmpty()) {
            this.ulid = UlidCreator.getUlid().toString();
        }
    }
}
