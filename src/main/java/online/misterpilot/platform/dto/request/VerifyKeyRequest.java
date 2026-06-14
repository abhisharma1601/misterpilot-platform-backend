package online.misterpilot.platform.dto.request;

import lombok.Data;

@Data
public class VerifyKeyRequest {
    private String apiKey;

    @Override
    public String toString() {
        return "VerifyKeyRequest{apiKey=***}";
    }
}
