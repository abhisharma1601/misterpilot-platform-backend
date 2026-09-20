package online.misterpilot.platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private String token;
    private Long userId;
    private String name;
    private String email;

    /**
     * Whether the account is activated. {@code false} means the email is
     * unverified — the frontend should render the "verify your email" screen.
     * When {@code false}, {@link #token} is {@code null} (no session is granted).
     */
    private Boolean active;
}
