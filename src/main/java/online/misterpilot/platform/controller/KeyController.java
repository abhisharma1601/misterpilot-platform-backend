package online.misterpilot.platform.controller;

import online.misterpilot.platform.dto.request.DisableKeyRequest;
import online.misterpilot.platform.dto.request.VerifyKeyRequest;
import online.misterpilot.platform.dto.response.ApiKeyResponse;
import online.misterpilot.platform.dto.response.DisableKeyResponse;
import online.misterpilot.platform.dto.response.GenerateKeyResponse;
import online.misterpilot.platform.dto.response.VerifyKeyResponse;
import online.misterpilot.platform.entity.ApiKey;
import online.misterpilot.platform.entity.User;
import online.misterpilot.platform.service.ApiKeyService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/keys")
@RequiredArgsConstructor
public class KeyController {

    private final ApiKeyService apiKeyService;

    /**
     * 1. Generate a new API key for the authenticated user.
     * The plain key is returned ONCE — it is never stored.
     */
    @PostMapping("/generate")
    public ResponseEntity<GenerateKeyResponse> generateKey() {
        User user = getAuthenticatedUser();

        String plainKey = apiKeyService.generateKey(user);

        return ResponseEntity.ok(
                GenerateKeyResponse.builder()
                        .apiKey(plainKey)
                        .message("Store this key safely — it will not be shown again.")
                        .build());
    }

    /**
     * 2. Verify an API key (called by Python Gateway before forwarding to DeepSeek).
     * Checks: key exists, is active, and the owning user has minimum ₹5 balance.
     * No authentication required.
     */
    @PostMapping("/verify")
    public ResponseEntity<VerifyKeyResponse> verifyKey(@RequestBody VerifyKeyRequest request) {
        boolean valid = apiKeyService.verifyKey(request.getApiKey());

        return ResponseEntity.ok(
                VerifyKeyResponse.builder()
                        .valid(valid)
                        .message(valid
                                ? "Valid key"
                                : "Invalid key, disabled, or insufficient balance (min ₹5 required)")
                        .build());
    }

    /**
     * 3. Get all active API keys for the authenticated user.
     */
    @GetMapping("/active")
    public ResponseEntity<List<ApiKeyResponse>> getActiveKeys() {
        User user = getAuthenticatedUser();

        List<ApiKeyResponse> keys = apiKeyService.getActiveKeys(user).stream()
                .map(key -> ApiKeyResponse.builder()
                        .id(key.getId())
                        .keyPlaceholder(key.getKeyPlaceholder())
                        .active(key.getActive())
                        .createdAt(key.getCreatedAt() != null ? key.getCreatedAt().toLocalDate() : null)
                        .lastUsedAt(key.getLastUsedAt() != null ? key.getLastUsedAt().toLocalDate() : null)
                        .build())
                .toList();

        return ResponseEntity.ok(keys);
    }

    /**
     * 4. Disable (deactivate) an API key owned by the authenticated user.
     * The key is not deleted — just marked inactive.
     */
    @PostMapping("/disable")
    public ResponseEntity<DisableKeyResponse> disableKey(@RequestBody DisableKeyRequest request) {
        User user = getAuthenticatedUser();
        boolean success = apiKeyService.disableKey(request.getApiKeyId(), user);

        return ResponseEntity.ok(
                DisableKeyResponse.builder()
                        .success(success)
                        .message(success
                                ? "Key disabled successfully"
                                : "Key not found or does not belong to your account")
                        .build());
    }

    /**
     * Extracts the authenticated User from the SecurityContext.
     */
    private User getAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated()) {
            throw new IllegalStateException("No authenticated user");
        }

        if (!(auth.getPrincipal() instanceof User)) {
            throw new IllegalStateException(
                    "Principal is not a User — check JWT filter mapping");
        }

        return (User) auth.getPrincipal();
    }
}
