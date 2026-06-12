package miras.monitor.User.Model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.security.Principal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserPrincipal implements Principal {
    private String userId;
    private String orgId;
    private String role;

    @Override
    public String getName() {
        return this.userId;
    }
}
