package online.misterpilot.platform.dto.request;

import lombok.Data;

@Data
public class DisableKeyRequest {
    private int apiKeyId;

    @Override
    public String toString() {
        return "DisableKeyRequest{apiKeyId=***}";
    }
}
