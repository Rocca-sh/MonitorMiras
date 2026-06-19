package miras.monitor.User.Controller.Dto;

import lombok.Data;

@Data
public class LoginCodeVerifyDto {
    private String key;    // Puede ser email o teléfono
    private String psswd;  // El código OTP
}
