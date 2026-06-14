package online.misterpilot.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "google")
public class GoogleProperties {
    
    private String clientId;
    private String clientSecret;

}
