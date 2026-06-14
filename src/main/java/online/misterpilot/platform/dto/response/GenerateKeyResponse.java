package online.misterpilot.platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateKeyResponse {
    private String apiKey;    // plain key — shown only once
    private String message;   // e.g. "Store this key safely"
}
