package miras.monitor.Org.Model;

import jakarta.persistence.*;
import miras.monitor.lib.SipCreator;
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
    @Column(length = 10)
    private String sipId;

    private String name;
    private String email;
    private String passwd;
}
