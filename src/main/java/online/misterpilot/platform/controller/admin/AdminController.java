package online.misterpilot.platform.controller.admin;

import lombok.RequiredArgsConstructor;
import online.misterpilot.platform.dto.admin.response.CashFlowSummaryDto;
import online.misterpilot.platform.dto.admin.response.ProviderFundingSummaryDto;
import online.misterpilot.platform.dto.admin.response.TransactionDto;
import online.misterpilot.platform.dto.admin.response.UserDto;
import online.misterpilot.platform.service.admin.CashFlowService;
import online.misterpilot.platform.service.admin.ProviderFundingService;
import online.misterpilot.platform.service.admin.TransactionDataService;
import online.misterpilot.platform.service.admin.UserDataService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserDataService userDataService;
    private final TransactionDataService transactionDataService;
    private final ProviderFundingService providerFundingService;
    private final CashFlowService cashFlowService;

    @GetMapping("/users")
    public ResponseEntity<Page<UserDto>> getUsers(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        PageRequest pageable = PageRequest.of(page, size);
        Page<UserDto> result = userDataService.getUsers(pageable);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/transactions")
    public ResponseEntity<Page<TransactionDto>> getTransactions(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        PageRequest pageable = PageRequest.of(page, size);
        Page<TransactionDto> result = transactionDataService.getTransactions(pageable);
        return ResponseEntity.ok(result);
    }

    /**
     * How much balance to hold with each provider to cover all user wallets.
     * {@code days} sets the usage window for the per-provider split; omit it for all-time.
     */
    @GetMapping("/provider-funding")
    public ResponseEntity<ProviderFundingSummaryDto> getProviderFunding(@RequestParam(required = false) Integer days) {
        return ResponseEntity.ok(providerFundingService.getProviderFunding(days));
    }

    /**
     * Revenue (successful recharges) all-time and for the current week, month and quarter,
     * each with transaction count, average and growth vs the previous period.
     */
    @GetMapping("/cashflow")
    public ResponseEntity<CashFlowSummaryDto> getCashFlow() {
        return ResponseEntity.ok(cashFlowService.getCashFlow());
    }
}
