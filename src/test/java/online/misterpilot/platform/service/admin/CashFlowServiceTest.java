package online.misterpilot.platform.service.admin;

import online.misterpilot.platform.dto.admin.response.CashFlowPeriodDto;
import online.misterpilot.platform.dto.admin.response.CashFlowSummaryDto;
import online.misterpilot.platform.enums.TransactionStatus;
import online.misterpilot.platform.enums.TransactionType;
import online.misterpilot.platform.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CashFlowService")
class CashFlowServiceTest {

    // Wednesday, 12 Aug 2026 — Q3 started 1 Jul, month 1 Aug, week Mon 10 Aug
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 12, 15, 30);

    @Mock private TransactionRepository transactionRepository;

    private CashFlowService service;

    @BeforeEach
    void setUp() {
        service = new CashFlowService(transactionRepository);
        when(transactionRepository.sumAmountByTypeAndStatusBetween(any(), any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(transactionRepository.countByTypeAndStatusBetween(any(), any(), any(), any())).thenReturn(0L);
    }

    private void givenRevenue(LocalDateTime from, LocalDateTime to, String amount, long count) {
        when(transactionRepository.sumAmountByTypeAndStatusBetween(
                eq(TransactionType.RECHARGE), eq(TransactionStatus.SUCCESS), eq(from), eq(to)))
                .thenReturn(new BigDecimal(amount));
        when(transactionRepository.countByTypeAndStatusBetween(
                eq(TransactionType.RECHARGE), eq(TransactionStatus.SUCCESS), eq(from), eq(to)))
                .thenReturn(count);
    }

    @Test
    @DisplayName("periods start on Monday, the 1st of the month and the 1st of the quarter")
    void periodBoundaries() {
        CashFlowSummaryDto summary = service.getCashFlow(NOW);

        assertThat(summary.getWeek().getPeriodStart()).isEqualTo(LocalDateTime.of(2026, 8, 10, 0, 0));
        assertThat(summary.getMonth().getPeriodStart()).isEqualTo(LocalDateTime.of(2026, 8, 1, 0, 0));
        assertThat(summary.getQuarter().getPeriodStart()).isEqualTo(LocalDateTime.of(2026, 7, 1, 0, 0));
        assertThat(summary.getWeek().getPeriodEnd()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("previous period covers the same elapsed span")
    void previousPeriodSameSpan() {
        CashFlowPeriodDto month = service.getCashFlow(NOW).getMonth();

        assertThat(month.getPreviousPeriodStart()).isEqualTo(LocalDateTime.of(2026, 7, 1, 0, 0));
        assertThat(month.getPreviousPeriodEnd()).isEqualTo(LocalDateTime.of(2026, 7, 12, 15, 30));
    }

    @Test
    @DisplayName("previous period is capped at the current period's start")
    void previousPeriodCapped() {
        // 31 Mar: previous month (Feb) is shorter than the elapsed span
        CashFlowPeriodDto month = service.getCashFlow(LocalDateTime.of(2026, 3, 31, 12, 0)).getMonth();

        assertThat(month.getPreviousPeriodStart()).isEqualTo(LocalDateTime.of(2026, 2, 1, 0, 0));
        assertThat(month.getPreviousPeriodEnd()).isEqualTo(LocalDateTime.of(2026, 3, 1, 0, 0));
    }

    @Test
    @DisplayName("computes revenue, count, average and growth")
    void revenueAverageGrowth() {
        LocalDateTime weekStart = LocalDateTime.of(2026, 8, 10, 0, 0);
        givenRevenue(weekStart, NOW, "18420.50", 482);
        givenRevenue(LocalDateTime.of(2026, 8, 3, 0, 0), LocalDateTime.of(2026, 8, 5, 15, 30), "17000.00", 400);

        CashFlowPeriodDto week = service.getCashFlow(NOW).getWeek();

        assertThat(week.getRevenueInr()).isEqualByComparingTo("18420.50");
        assertThat(week.getTransactionCount()).isEqualTo(482);
        assertThat(week.getAverageTransactionInr()).isEqualByComparingTo("38.22");
        assertThat(week.getPreviousRevenueInr()).isEqualByComparingTo("17000.00");
        assertThat(week.getGrowthPercent()).isEqualByComparingTo("8.4");
    }

    @Test
    @DisplayName("growth is null and average zero when there is no data")
    void noData() {
        CashFlowSummaryDto summary = service.getCashFlow(NOW);

        assertThat(summary.getTotalRevenueInr()).isEqualByComparingTo("0");
        assertThat(summary.getQuarter().getAverageTransactionInr()).isEqualByComparingTo("0");
        assertThat(summary.getQuarter().getGrowthPercent()).isNull();
    }
}
