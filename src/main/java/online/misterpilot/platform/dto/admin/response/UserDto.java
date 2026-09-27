package online.misterpilot.platform.dto.admin.response;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UserDto {
    private Long id;
    private String name;
    private String email;
    private BigDecimal balance;
    private Boolean active;
    private LocalDateTime createdAt;
}
