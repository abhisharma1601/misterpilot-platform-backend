package online.misterpilot.platform.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TransactionType} and {@link TransactionStatus} enums.
 */
@DisplayName("Enums")
class EnumsTest {

    @Nested
    @DisplayName("TransactionType")
    class TransactionTypeTest {

        @Test
        @DisplayName("Should have exactly 4 values")
        void shouldHaveFourValues() {
            assertThat(TransactionType.values()).hasSize(4);
        }

        @Test
        @DisplayName("Should contain RECHARGE")
        void shouldContainRecharge() {
            assertThat(TransactionType.valueOf("RECHARGE")).isEqualTo(TransactionType.RECHARGE);
        }

        @Test
        @DisplayName("Should contain USAGE_CHARGE")
        void shouldContainUsageCharge() {
            assertThat(TransactionType.valueOf("USAGE_CHARGE")).isEqualTo(TransactionType.USAGE_CHARGE);
        }

        @Test
        @DisplayName("Should contain REFUND")
        void shouldContainRefund() {
            assertThat(TransactionType.valueOf("REFUND")).isEqualTo(TransactionType.REFUND);
        }

        @Test
        @DisplayName("Should contain ADJUSTMENT")
        void shouldContainAdjustment() {
            assertThat(TransactionType.valueOf("ADJUSTMENT")).isEqualTo(TransactionType.ADJUSTMENT);
        }
    }

    @Nested
    @DisplayName("TransactionStatus")
    class TransactionStatusTest {

        @Test
        @DisplayName("Should have exactly 3 values")
        void shouldHaveThreeValues() {
            assertThat(TransactionStatus.values()).hasSize(3);
        }

        @Test
        @DisplayName("Should contain PENDING")
        void shouldContainPending() {
            assertThat(TransactionStatus.valueOf("PENDING")).isEqualTo(TransactionStatus.PENDING);
        }

        @Test
        @DisplayName("Should contain SUCCESS")
        void shouldContainSuccess() {
            assertThat(TransactionStatus.valueOf("SUCCESS")).isEqualTo(TransactionStatus.SUCCESS);
        }

        @Test
        @DisplayName("Should contain FAILED")
        void shouldContainFailed() {
            assertThat(TransactionStatus.valueOf("FAILED")).isEqualTo(TransactionStatus.FAILED);
        }
    }
}
