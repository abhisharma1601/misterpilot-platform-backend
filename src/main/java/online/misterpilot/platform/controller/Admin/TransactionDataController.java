package online.misterpilot.platform.controller.Admin;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;
import online.misterpilot.platform.dto.Admin.response.TransactionDto;
import online.misterpilot.platform.service.Admin.TransactionDataService;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/admin")
public class TransactionDataController {

    final private TransactionDataService transactionDataService;

    @GetMapping("/transactions")
    public ResponseEntity<Page<TransactionDto>> getTransactions(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        PageRequest pageable = PageRequest.of(page, size);
        Page<TransactionDto> result = transactionDataService.getTransactions(pageable);
        return ResponseEntity.ok(result);
    }
}
