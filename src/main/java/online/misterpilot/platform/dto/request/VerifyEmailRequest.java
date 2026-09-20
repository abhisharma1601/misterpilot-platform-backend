package online.misterpilot.platform.dto.request;

import lombok.Data;

@Data
public class VerifyEmailRequest {
    private String token;
}
