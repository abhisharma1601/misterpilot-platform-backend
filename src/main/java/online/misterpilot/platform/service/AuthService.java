package online.misterpilot.platform.service;

import online.misterpilot.platform.dto.request.RegisterRequest;
import online.misterpilot.platform.dto.response.LoginResponse;
import online.misterpilot.platform.entity.PasswordResetToken;
import online.misterpilot.platform.entity.User;
import online.misterpilot.platform.repository.PasswordResetTokenRepository;
import online.misterpilot.platform.repository.UserRepository;
import online.misterpilot.platform.util.AuthUtil;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final WalletService walletService;
    private final AuthUtil authUtil;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailService emailService;

    // ==================== Email/Password Login ====================

    public LoginResponse login(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

        if (user.getPasswordHash() == null) {
            throw new IllegalArgumentException(
                    "This account uses Google sign-in. Please log in with Google.");
        }

        if (!authUtil.verifyPassword(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        String token = authUtil.generateJwt(user);

        log.info("User logged in (email/password): id={}, email={}", user.getId(), user.getEmail());

        return buildLoginResponse(user, token);
    }

    // ==================== Email/Password Registration ====================

    @Transactional
    public LoginResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already registered: " + request.getEmail());
        }

        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new IllegalArgumentException("Password is required for registration");
        }

        String passwordHash = authUtil.hashPassword(request.getPassword());
        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .passwordHash(passwordHash)
                .build();
        user = userRepository.save(user);

        walletService.createWallet(user);

        String token = authUtil.generateJwt(user);

        log.info("User registered (email/password): id={}, email={}", user.getId(), user.getEmail());

        return buildLoginResponse(user, token);
    }

    // ==================== Google OAuth Registration / Link ====================

    @Transactional
    public LoginResponse registerGoogleUser(String googleId, String name, String email) {
        User user = userRepository.findByEmail(email).orElse(null);

        if (user != null) {
            // Existing user (e.g. email/password signup) — link Google ID so
            // they can sign in with either method going forward.
            user.setGoogleId(googleId);
            user = userRepository.save(user);
            log.info("Google ID linked to existing user: id={}, email={}, googleId={}",
                    user.getId(), email, googleId);
        } else {
            // Brand new Google user
            user = User.builder()
                    .googleId(googleId)
                    .name(name)
                    .email(email)
                    .passwordHash(null)
                    .build();
            user = userRepository.save(user);
            walletService.createWallet(user);
            log.info("User registered (Google): id={}, email={}, googleId={}",
                    user.getId(), email, googleId);
        }

        String token = authUtil.generateJwt(user);
        return buildLoginResponse(user, token);
    }

    // ==================== Password Reset ====================

    @Transactional
    public void initiatePasswordReset(String email) {
        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            throw new IllegalArgumentException("No account found with this email");
        }

        // Invalidate any existing tokens for this email
        passwordResetTokenRepository.deleteAllByEmail(email);

        String token = UUID.randomUUID().toString();
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .email(email)
                .token(token)
                .expiresAt(LocalDateTime.now().plusMinutes(15))
                .build();
        passwordResetTokenRepository.save(resetToken);

        emailService.sendPasswordResetLink(email, user.getName(), token);
        log.info("Password reset link sent: email={}", email);
    }

    // ==================== Reset Password ====================

    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired reset link"));

        if (resetToken.getUsed()) {
            throw new IllegalArgumentException("Reset link has already been used");
        }

        if (resetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Reset link has expired");
        }

        if (newPassword == null || newPassword.isBlank() || newPassword.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters");
        }

        User user = userRepository.findByEmail(resetToken.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));

        user.setPasswordHash(authUtil.hashPassword(newPassword));
        userRepository.save(user);

        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);

        log.info("Password reset successful: email={}", resetToken.getEmail());
    }

    // ==================== Shared Helpers ====================

    private LoginResponse buildLoginResponse(User user, String token) {
        return LoginResponse.builder()
                .token(token)
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .build();
    }

}
