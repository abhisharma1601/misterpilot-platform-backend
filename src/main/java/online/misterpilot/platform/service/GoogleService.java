package online.misterpilot.platform.service;

import online.misterpilot.platform.config.GoogleProperties;
import online.misterpilot.platform.dto.request.GoogleLoginRequest;
import online.misterpilot.platform.dto.response.LoginResponse;
import online.misterpilot.platform.entity.User;
import online.misterpilot.platform.repository.UserRepository;
import online.misterpilot.platform.util.AuthUtil;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleService {

    private final UserRepository userRepository;
    private final AuthService authService;
    private final AuthUtil authUtil;
    private final GoogleProperties googleProperties;
    private final RestTemplate restTemplate = new RestTemplate();

    private static final String GOOGLE_TOKENINFO_URL =
            "https://oauth2.googleapis.com/tokeninfo?id_token=";

    public LoginResponse authenticate(GoogleLoginRequest request) {
        // 1. Verify idToken with Google and extract claims
        Map<String, Object> claims = verifyGoogleToken(request.getIdToken());
        String googleId = (String) claims.get("sub");
        String email = (String) claims.get("email");
        String name = (String) claims.get("name");

        // 2. Look up existing user by googleId
        User user = userRepository.findByGoogleId(googleId).orElse(null);

        // 3. If not present, register a new user via Google
        if (user == null) {
            log.info("No existing user for googleId={} — registering new Google user", googleId);
            return authService.registerGoogleUser(googleId, name, email);
        }

        // 4. User exists — build LoginResponse with JWT
        log.info("User found for googleId={}: id={}, email={}", googleId, user.getId(), user.getEmail());

        return LoginResponse.builder()
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .token(authUtil.generateJwt(user))
                .build();
    }

    /**
     * Calls Google's tokeninfo endpoint to validate the idToken
     * and returns the full claims map.
     *
     * Throws IllegalArgumentException (→ 400) if the token is
     * invalid, expired, or was not issued for this application.
     */
    private Map<String, Object> verifyGoogleToken(String idToken) {
        log.debug("Verifying Google idToken");
        String url = GOOGLE_TOKENINFO_URL + idToken;

        Map<String, Object> claims;
        try {
            claims = restTemplate.getForObject(url, Map.class);
        } catch (Exception e) {
            log.warn("Google token verification failed: {}", e.getMessage());
            throw new IllegalArgumentException("Invalid or expired Google ID token");
        }

        if (claims == null || claims.get("sub") == null) {
            throw new IllegalArgumentException("Invalid Google ID token — missing user identity");
        }

        if (!googleProperties.getClientId().equals(claims.get("aud"))) {
            log.error("Google token aud={} does not match expected clientId",
                    claims.get("aud"));
            throw new IllegalArgumentException("Token was not issued for this application");
        }

        return claims;
    }

}
