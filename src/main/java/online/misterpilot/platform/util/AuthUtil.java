package online.misterpilot.platform.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import online.misterpilot.platform.entity.User;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class AuthUtil {

    private final SecretKey signingKey;
    private final BCryptPasswordEncoder passwordEncoder;

    public AuthUtil(
            @Value("${app.auth.jwt-secret}") String jwtSecret) {
        this.signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    // ── Token generation ──────────────────────────────────────

    public String generateJwt(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + 7 * 24 * 60 * 60 * 1000L);

        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("name", user.getName())
                .claim("googleId", user.getGoogleId())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    // ── Token parsing / validation ────────────────────────────

    /**
     * Parses and validates a JWT. Returns all claims on success,
     * throws a JwtException with a clear message on failure.
     */
    public Claims parseJwt(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Validates the token AND returns the failure reason in one parse pass.
     * Returns null if valid, or an error message string if invalid.
     *
     * This is the single method the filter should call — avoids parsing twice.
     */
    public String validateToken(String token) {
        if (token == null || token.isBlank()) {
            return "Missing token";
        }
        try {
            parseJwt(token);
            return null; // valid
        } catch (ExpiredJwtException e) {
            return "Token expired at " + e.getClaims().getExpiration();
        } catch (SignatureException e) {
            return "Invalid signature";
        } catch (MalformedJwtException e) {
            return "Malformed token";
        } catch (JwtException e) {
            return "Invalid token: " + e.getMessage();
        } catch (Exception e) {
            return "Token validation failed: " + e.getMessage();
        }
    }

    /**
     * Returns true if the token is well-formed, correctly signed,
     * and not expired.
     */
    public boolean isJwtValid(String token) {
        return validateToken(token) == null;
    }

    /**
     * Returns a human-readable reason why the token is invalid,
     * or null if it's valid.
     */
    public String getJwtFailureReason(String token) {
        return validateToken(token);
    }

    /**
     * Extracts the user ID (subject) from the JWT, or throws
     * if the token is invalid.
     */
    public Long getUserIdFromJwt(String token) {
        String sub = parseJwt(token).getSubject();
        return Long.parseLong(sub);
    }

    // ── Password helpers ──────────────────────────────────────

    public String hashPassword(String plainPassword) {
        return passwordEncoder.encode(plainPassword);
    }

    public boolean verifyPassword(String plainPassword, String hash) {
        return passwordEncoder.matches(plainPassword, hash);
    }

    // ── Current user ───────────────────────────────────────────

    /**
     * Returns the currently authenticated User from the security context,
     * or throws if no authentication is present.
     */
    public User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User)) {
            throw new IllegalStateException("No authenticated user in security context");
        }
        return (User) auth.getPrincipal();
    }
}
