package online.misterpilot.platform.dto.Admin.response;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UserDto {
    public Long id;
    public String name;
    public String email;
    public BigDecimal balance;
    public Boolean active;
    public LocalDateTime createdAt;
}
