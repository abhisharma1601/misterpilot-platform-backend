package online.misterpilot.platform.controller;

import online.misterpilot.platform.dto.request.UsageChargeRequest;
import online.misterpilot.platform.dto.response.CostCalculationResponse;
import online.misterpilot.platform.service.KeyUsageService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/internal/usage")
@RequiredArgsConstructor
public class UsageController {

    private final KeyUsageService keyUsageService;

    @PostMapping("/charge")
    public ResponseEntity<CostCalculationResponse> charge(
            @RequestBody UsageChargeRequest request) {

        CostCalculationResponse response = keyUsageService.chargeUsage(
                request.getApiKey(),
                request.getCostInr(),
                request.getModel(),
                request.getOutputTokens(),
                request.getCacheHitTokens(),
                request.getCacheMissTokens());

        return ResponseEntity.ok(response);
    }
}
