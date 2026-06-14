package online.misterpilot.platform.controller;

import online.misterpilot.platform.dto.response.ApiKeyResponse;
import online.misterpilot.platform.dto.response.UserStatsForDateResponse;
import online.misterpilot.platform.dto.response.UserStatsResponse;
import online.misterpilot.platform.dto.response.WalletSummaryResponse;
import online.misterpilot.platform.entity.User;
import online.misterpilot.platform.service.UserStatsService;
import online.misterpilot.platform.util.AuthUtil;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/stats")
@RequiredArgsConstructor
public class StatsController {

    private final UserStatsService userStatsService;
    private final AuthUtil authUtil;

    /**
     * Full dashboard stats for the authenticated user.
     */
    @GetMapping("")
    public ResponseEntity<UserStatsResponse> getStats() {
        User user = authUtil.getCurrentUser();
        return ResponseEntity.ok(userStatsService.getStats(user));
    }

    /**
     * Tokens consumed & cost incurred on a specific date.
     * Query param: ?date=2025-07-14
     */
    @GetMapping("/date")
    public ResponseEntity<UserStatsForDateResponse> getStatsForDate(
            @RequestParam("date")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date) {

        User user = authUtil.getCurrentUser();
        return ResponseEntity.ok(userStatsService.getStatsForDate(user, date));
    }

    /**
     * All API keys belonging to the authenticated user.
     */
    @GetMapping("/keys")
    public ResponseEntity<List<ApiKeyResponse>> getKeys() {
        User user = authUtil.getCurrentUser();
        return ResponseEntity.ok(userStatsService.getApiKeys(user));
    }

    /**
     * Wallet summary: current balance, total recharged, total consumed, last 5 recharges.
     */
    @GetMapping("/wallet-summary")
    public ResponseEntity<WalletSummaryResponse> getWalletSummary() {
        User user = authUtil.getCurrentUser();
        return ResponseEntity.ok(userStatsService.getWalletSummary(user));
    }
}
