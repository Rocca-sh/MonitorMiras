package miras.monitor.User.Controller.Dto;

import lombok.Data;

@Data
public class LoginCodeVerifyDto {
    private String key;    // Puede ser email o telefono
    private String psswd;  // El codigo OTP
}
