package online.misterpilot.platform.controller.Admin;

import lombok.RequiredArgsConstructor;
import online.misterpilot.platform.entity.User;
import online.misterpilot.platform.util.AuthUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AuthUtil authUtil;

    @GetMapping("/demo")
    public ResponseEntity<Map<String, Object>> demo() {
        User user = authUtil.getCurrentUser();

        return ResponseEntity.ok(Map.of(
                "message", "Welcome to the admin demo!",
                "userId", user.getId(),
                "name", user.getName(),
                "email", user.getEmail(),
                "role", user.getRole().name()
        ));
    }
}
