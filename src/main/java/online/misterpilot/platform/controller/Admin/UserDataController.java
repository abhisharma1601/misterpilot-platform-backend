package online.misterpilot.platform.controller.Admin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;
import online.misterpilot.platform.dto.Admin.response.UserDto;
import online.misterpilot.platform.service.Admin.UserDataService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin")
public class UserDataController {

    final private UserDataService userDataService;


    @GetMapping("/users")
    public ResponseEntity<Page<UserDto>> getUsers(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        PageRequest pageable = PageRequest.of(page, size);

        Page<UserDto> result = userDataService.getUsers(pageable);

        return ResponseEntity.ok(result);
    }

    
    
    
}
