package miras.monitor.User.Service;

import miras.monitor.User.Model.User;

public interface UserAuthServ {
    
    User createUser(User user);

    User loginPsswd(String email, String psswd);
    
    User getUserByEmail(String email);

    User linkUserToOrg(User user, String code);
}
