package online.misterpilot.platform.controller;

import lombok.RequiredArgsConstructor;
import online.misterpilot.platform.dto.response.ProfileResponse;
import online.misterpilot.platform.entity.User;
import online.misterpilot.platform.service.ProfileService;
import online.misterpilot.platform.util.AuthUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;
    private final AuthUtil authUtil;

    @GetMapping("/me")
    public ResponseEntity<ProfileResponse> getProfile() {
        User user = authUtil.getCurrentUser();
        return ResponseEntity.ok(profileService.getProfile(user));
    }

    @DeleteMapping("/me")
    public ResponseEntity<Map<String, String>> deleteAccount() {
        User user = authUtil.getCurrentUser();
        profileService.deleteAccount(user);
        return ResponseEntity.ok(Map.of("message", "Account deleted"));
    }
}
