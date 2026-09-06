package online.misterpilot.platform.service.Admin;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import online.misterpilot.platform.dto.Admin.response.UserDto;
import online.misterpilot.platform.entity.User;
import online.misterpilot.platform.entity.Wallet;
import online.misterpilot.platform.enums.RoleType;
import online.misterpilot.platform.repository.UserRepository;
import online.misterpilot.platform.repository.WalletRepository;

@Service
@RequiredArgsConstructor
public class UserDataService {
    
    final private UserRepository userRepository;
    final private WalletRepository walletRepository;

    public Page<UserDto> getUsers(Pageable pageable) {
        Page<User> userPage = userRepository.findByRole(RoleType.USER, pageable);
        List<User> users = userPage.getContent();
        Map<User, BigDecimal> balanceMap = walletRepository.findByUserIn(users)
                .stream()
                .collect(Collectors.toMap(Wallet::getUser, Wallet::getBalance));
        return userPage.map(user -> toDTO(user, balanceMap.getOrDefault(user, BigDecimal.ZERO)));
    }

    private UserDto toDTO(User user, BigDecimal balance) {
        UserDto userDto = new UserDto();
        userDto.setId(user.getId());
        userDto.setActive(user.getActive());
        userDto.setEmail(user.getEmail());
        userDto.setName(user.getName());
        userDto.setCreatedAt(user.getCreatedAt());
        userDto.setBalance(balance);
        return userDto;
    }
}
