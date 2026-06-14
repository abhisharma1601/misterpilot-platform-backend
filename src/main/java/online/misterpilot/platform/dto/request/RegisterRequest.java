package online.misterpilot.platform.dto.request;

import jakarta.annotation.Nullable;
import lombok.Data;

@Data
public class RegisterRequest {
    private String name;
    private String email;

    @Nullable
    private String password;
}
