package online.misterpilot.platform.dto.request;

import lombok.Data;

@Data
public class LoginRequest {
    private String email;
    private String password;
}
