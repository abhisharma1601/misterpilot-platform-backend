package online.misterpilot.platform.service.admin;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import online.misterpilot.platform.dto.admin.response.CashFlowPeriodDto;
import online.misterpilot.platform.dto.admin.response.CashFlowSummaryDto;
import online.misterpilot.platform.enums.TransactionStatus;
import online.misterpilot.platform.enums.TransactionType;
import online.misterpilot.platform.repository.TransactionRepository;

/**
 * Revenue figures for the admin CashFlow dashboard. Revenue is the sum of successful
 * wallet recharges.
 *
 * Week, month and quarter are calendar periods to date (week starts Monday). Growth
 * compares against the same span of the previous period, so a period that has just
 * started is not measured against a full one.
 */
@Service
@RequiredArgsConstructor
public class CashFlowService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final LocalDateTime EPOCH = LocalDateTime.of(1970, 1, 1, 0, 0);

    private final TransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public CashFlowSummaryDto getCashFlow() {
        return getCashFlow(LocalDateTime.now());
    }

    CashFlowSummaryDto getCashFlow(LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        LocalDateTime weekStart = today.with(DayOfWeek.MONDAY).atStartOfDay();
        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();
        int quarterFirstMonth = (today.getMonthValue() - 1) / 3 * 3 + 1;
        LocalDateTime quarterStart = LocalDate.of(today.getYear(), quarterFirstMonth, 1).atStartOfDay();

        CashFlowSummaryDto summary = new CashFlowSummaryDto();
        summary.setTotalRevenueInr(revenue(EPOCH, now));
        summary.setTotalTransactionCount(count(EPOCH, now));
        summary.setGeneratedAt(now);
        summary.setWeek(period(weekStart, weekStart.minusWeeks(1), now));
        summary.setMonth(period(monthStart, monthStart.minusMonths(1), now));
        summary.setQuarter(period(quarterStart, quarterStart.minusMonths(3), now));
        return summary;
    }

    private CashFlowPeriodDto period(LocalDateTime start, LocalDateTime previousStart, LocalDateTime now) {
        // Same elapsed span in the previous period, never running past the current period's start
        LocalDateTime previousEnd = previousStart.plus(Duration.between(start, now));
        if (previousEnd.isAfter(start)) {
            previousEnd = start;
        }

        BigDecimal revenue = revenue(start, now);
        long count = count(start, now);
        BigDecimal previousRevenue = revenue(previousStart, previousEnd);

        BigDecimal average = count == 0
                ? BigDecimal.ZERO.setScale(2)
                : revenue.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
        BigDecimal growth = previousRevenue.signum() == 0
                ? null
                : revenue.subtract(previousRevenue).multiply(HUNDRED).divide(previousRevenue, 1, RoundingMode.HALF_UP);

        return new CashFlowPeriodDto(start, now, revenue, count, average,
                previousStart, previousEnd, previousRevenue, growth);
    }

    private BigDecimal revenue(LocalDateTime from, LocalDateTime to) {
        return transactionRepository
                .sumAmountByTypeAndStatusBetween(TransactionType.RECHARGE, TransactionStatus.SUCCESS, from, to)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private long count(LocalDateTime from, LocalDateTime to) {
        return transactionRepository
                .countByTypeAndStatusBetween(TransactionType.RECHARGE, TransactionStatus.SUCCESS, from, to);
    }
}
