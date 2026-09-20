package online.misterpilot.platform.auth;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import online.misterpilot.platform.dto.request.ForgotPasswordRequest;
import online.misterpilot.platform.dto.request.GoogleLoginRequest;
import online.misterpilot.platform.dto.request.LoginRequest;
import online.misterpilot.platform.dto.request.RegisterRequest;
import online.misterpilot.platform.dto.request.ResendVerificationRequest;
import online.misterpilot.platform.dto.request.ResetPasswordRequest;
import online.misterpilot.platform.dto.request.VerifyEmailRequest;
import online.misterpilot.platform.dto.response.LoginResponse;
import online.misterpilot.platform.dto.response.MessageResponse;
import online.misterpilot.platform.service.AuthService;
import online.misterpilot.platform.service.GoogleService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;


@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final GoogleService googleService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request.getEmail(), request.getPassword());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/google/login")
    public ResponseEntity<LoginResponse> googleLogin(@RequestBody GoogleLoginRequest request) {
        LoginResponse response = googleService.authenticate(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/register")
    public ResponseEntity<MessageResponse> register(@RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    /**
     * Consumes the token from the emailed verification link and activates
     * the account. Public endpoint — the token itself is the credential.
     */
    @PostMapping("/verify-email")
    public ResponseEntity<MessageResponse> verifyEmail(@RequestBody VerifyEmailRequest request) {
        authService.verifyEmail(request.getToken());
        return ResponseEntity.ok(
                new MessageResponse("Email verified. Your account is now active."));
    }

    /**
     * Re-sends the verification link for an account that hasn't been
     * activated yet. Invalidates any previously issued link.
     */
    @PostMapping("/resend-verification")
    public ResponseEntity<MessageResponse> resendVerification(
            @RequestBody ResendVerificationRequest request) {
        authService.resendVerificationEmail(request.getEmail());
        return ResponseEntity.ok(
                new MessageResponse("A new verification link has been sent."));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        authService.initiatePasswordReset(request.getEmail());
        return ResponseEntity.ok(Map.of("message", "If this email is registered, a reset link has been sent"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(Map.of("message", "Password reset successful"));
    }

}
