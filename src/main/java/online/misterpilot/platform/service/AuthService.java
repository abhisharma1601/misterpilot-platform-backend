package online.misterpilot.platform.service;

import online.misterpilot.platform.dto.request.RegisterRequest;
import online.misterpilot.platform.dto.response.LoginResponse;
import online.misterpilot.platform.dto.response.MessageResponse;
import online.misterpilot.platform.entity.EmailVerificationToken;
import online.misterpilot.platform.entity.PasswordResetToken;
import online.misterpilot.platform.entity.User;
import online.misterpilot.platform.enums.Role;
import online.misterpilot.platform.repository.EmailVerificationTokenRepository;
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

    /** How long an emailed verification link stays valid. */
    private static final long EMAIL_VERIFICATION_TTL_HOURS = 24;

    /** How long a password-reset link stays valid. */
    private static final long PASSWORD_RESET_TTL_MINUTES = 15;

    private final UserRepository userRepository;
    private final WalletService walletService;
    private final AuthUtil authUtil;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
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

        // Credentials are valid but the email was never verified.
        // Return 200 with active=false and NO token so the frontend can
        // render the "verify your email" screen instead of a session.
        if (!user.isActive()) {
            log.info("Login for unverified account (no session issued): id={}, email={}",
                    user.getId(), user.getEmail());
            return buildLoginResponse(user, null);
        }

        String token = authUtil.generateJwt(user);

        log.info("User logged in (email/password): id={}, email={}", user.getId(), user.getEmail());

        return buildLoginResponse(user, token);
    }

    // ==================== Email/Password Registration ====================

    /**
     * Creates an inactive user and emails them a verification link.
     * No JWT is issued — the account becomes usable only after the
     * link is consumed by {@link #verifyEmail(String)}.
     */
    @Transactional
    public MessageResponse register(RegisterRequest request) {
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
                .role(Role.USER)
                .active(false)   // activated by the emailed verification link
                .build();
        user = userRepository.save(user);

        walletService.createWallet(user);

        String verificationToken = issueEmailVerificationToken(user);
        emailService.sendEmailVerificationLink(user.getEmail(), user.getName(), verificationToken);
        emailService.sendWelcomeEmail(user.getEmail(), user.getName());

        log.info("User registered (email/password), verification link sent: id={}, email={}",
                user.getId(), user.getEmail());

        return new MessageResponse(
                "Registration successful. Please check your email to verify your account.");
    }

    // ==================== Google OAuth Registration / Link ====================

    @Transactional
    public LoginResponse registerGoogleUser(String googleId, String name, String email) {
        User user = userRepository.findByEmail(email).orElse(null);

        if (user != null) {
            // Existing user (e.g. email/password signup) — link Google ID so
            // they can sign in with either method going forward.
            // Google has already proven ownership of this address, so an
            // account that was still pending verification becomes active.
            user.setGoogleId(googleId);
            user.setActive(true);
            user = userRepository.save(user);
            log.info("Google ID linked to existing user: id={}, email={}, googleId={}",
                    user.getId(), email, googleId);
        } else {
            // Brand new Google user — Google-verified email, so active immediately.
            user = User.builder()
                    .googleId(googleId)
                    .name(name)
                    .email(email)
                    .passwordHash(null)
                    .role(Role.USER)
                    .active(true)
                    .build();
            user = userRepository.save(user);
            walletService.createWallet(user);
            emailService.sendWelcomeEmail(user.getEmail(), user.getName());
            log.info("User registered (Google, active): id={}, email={}, googleId={}",
                    user.getId(), email, googleId);
        }

        String token = authUtil.generateJwt(user);
        return buildLoginResponse(user, token);
    }

    // ==================== Email Verification ====================

    /**
     * Consumes an email-verification token and activates the matching account.
     * Called by the frontend when the user lands on /verify-email?token=...
     */
    @Transactional
    public void verifyEmail(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Verification token is required");
        }

        EmailVerificationToken verificationToken = emailVerificationTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired verification link"));

        if (Boolean.TRUE.equals(verificationToken.getUsed())) {
            throw new IllegalArgumentException("This verification link has already been used");
        }

        if (verificationToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("This verification link has expired");
        }

        User user = userRepository.findByEmail(verificationToken.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));

        if (!user.isActive()) {
            user.setActive(true);
            userRepository.save(user);
        }

        verificationToken.setUsed(true);
        emailVerificationTokenRepository.save(verificationToken);

        log.info("Email verified — account activated: id={}, email={}",
                user.getId(), user.getEmail());
    }

    /**
     * Re-issues a verification link for an account that hasn't been activated yet.
     * Rejects the request if a link is already outstanding (unused and unexpired),
     * so a user cannot be spammed with links. Only once the previous link has
     * expired or been consumed can a new one be requested.
     */
    @Transactional
    public void resendVerificationEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("No account found with this email"));

        if (user.isActive()) {
            throw new IllegalArgumentException("This email is already verified");
        }

        String verificationToken = issueEmailVerificationToken(user);
        emailService.sendEmailVerificationLink(user.getEmail(), user.getName(), verificationToken);

        log.info("Verification link re-sent: email={}", email);
    }

    /**
     * Invalidates any leftover (used/expired) verification tokens for the user
     * and persists a fresh single-use one. Returns the raw token (the only
     * value that ever leaves the server).
     *
     * Refuses to issue a new link while a previous one is still outstanding —
     * there is no point emailing a second link that the first would invalidate.
     */
    private String issueEmailVerificationToken(User user) {
        String email = user.getEmail();

        emailVerificationTokenRepository.findActiveByEmail(email, LocalDateTime.now())
                .ifPresent(existing -> {
                    throw new IllegalArgumentException(
                            "A verification link has already been sent to " + email
                                    + ". Please check your inbox, or try again once it expires.");
                });

        // Nothing active remains, so anything still stored is used or expired.
        emailVerificationTokenRepository.deleteAllByEmail(email);

        String token = UUID.randomUUID().toString();
        EmailVerificationToken verificationToken = EmailVerificationToken.builder()
                .email(email)
                .token(token)
                .expiresAt(LocalDateTime.now().plusHours(EMAIL_VERIFICATION_TTL_HOURS))
                .build();
        emailVerificationTokenRepository.save(verificationToken);

        return token;
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
                .expiresAt(LocalDateTime.now().plusMinutes(PASSWORD_RESET_TTL_MINUTES))
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

    /**
     * Builds the login payload. {@code token} is null when no session should
     * be granted (e.g. an unverified account), in which case {@code active}
     * is false and the frontend renders the verification screen.
     */
    private LoginResponse buildLoginResponse(User user, String token) {
        return LoginResponse.builder()
                .token(token)
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .active(user.isActive())
                .build();
    }

}
