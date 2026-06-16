package online.misterpilot.platform.service;

import online.misterpilot.platform.config.RazorpayProperties;
import online.misterpilot.platform.dto.request.CreateOrderRequest;
import online.misterpilot.platform.dto.request.TransactionRequest;
import online.misterpilot.platform.dto.response.CreateOrderResponse;
import online.misterpilot.platform.dto.response.TransactionResponse;
import online.misterpilot.platform.entity.Transaction;
import online.misterpilot.platform.entity.User;
import online.misterpilot.platform.entity.Wallet;
import online.misterpilot.platform.enums.TransactionStatus;
import online.misterpilot.platform.enums.TransactionType;
import online.misterpilot.platform.repository.TransactionRepository;
import online.misterpilot.platform.repository.WalletRepository;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("WalletService")
class WalletServiceTest {

    @Mock private WalletRepository walletRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private RazorpayService razorpayService;
    @Mock private RazorpayProperties razorpayProperties;
    @Mock private EmailService emailService;

    private WalletService walletService;
    private User testUser;

    // ── Helpers ─────────────────────────────────────────

    static User testUser() {
        return User.builder().id(1L).name("Test User")
                .email("test-example-com").build();
    }

    private Wallet walletWithBalance(String balance) {
        return Wallet.builder().id(10L).user(testUser)
                .balance(new BigDecimal(balance)).build();
    }

    private Transaction pendingTx(Wallet wallet, String orderId) {
        return Transaction.builder().id(100L).wallet(wallet)
                .type(TransactionType.RECHARGE)
                .status(TransactionStatus.PENDING)
                .amount(new BigDecimal("500.00")).orderId(orderId).build();
    }

    private TransactionRequest rechargeReq(String orderId, String paymentId,
                                            String signature) {
        TransactionRequest r = new TransactionRequest();
        r.setType(TransactionType.RECHARGE);
        r.setAmount(new BigDecimal("500.00"));
        r.setOrderId(orderId);
        r.setPaymentId(paymentId);
        r.setSignature(signature);
        return r;
    }

    private JSONObject capturedPayment(long paise) {
        JSONObject p = new JSONObject();
        p.put("status", "captured");
        p.put("amount", paise);
        return p;
    }

    @BeforeEach
    void setUp() {
        testUser = testUser();
        walletService = new WalletService(
                walletRepository, transactionRepository,
                razorpayService, razorpayProperties, emailService);
        lenient().when(razorpayProperties.getKeyId())
                .thenReturn("rzp_test_key123");
    }

    // ===========================================================
    //  createOrder
    // ===========================================================

    @Nested
    @DisplayName("createOrder()")
    class CreateOrder {

        @Test
        @DisplayName("Should create Razorpay order, save PENDING tx, return response")
        void shouldCreateOrder() {
            CreateOrderRequest req = CreateOrderRequest.builder()
                    .amount(new BigDecimal("500.00")).build();
            Wallet wallet = walletWithBalance("0.00");

            JSONObject rzpOrder = new JSONObject();
            rzpOrder.put("id", "order_A");
            rzpOrder.put("currency", "INR");
            rzpOrder.put("receipt", "rcpt_X");

            when(walletRepository.findByUser(testUser))
                    .thenReturn(Optional.of(wallet));
            when(razorpayService.createOrder(500L)).thenReturn(rzpOrder);
            Transaction saved = Transaction.builder().id(1L).build();
            when(transactionRepository.save(any(Transaction.class)))
                    .thenReturn(saved);

            CreateOrderResponse resp = walletService.createOrder(req, testUser);

            assertThat(resp.getOrderId()).isEqualTo("order_A");
            assertThat(resp.getTransactionId()).isEqualTo(1L);
            assertThat(resp.getAmount()).isEqualByComparingTo(
                    new BigDecimal("500.00"));
            assertThat(resp.getCurrency()).isEqualTo("INR");
            assertThat(resp.getKeyId()).isEqualTo("rzp_test_key123");
            assertThat(resp.getReceipt()).isEqualTo("rcpt_X");
        }

        @Test
        @DisplayName("Should throw on null amount")
        void shouldThrowNullAmount() {
            CreateOrderRequest req = CreateOrderRequest.builder()
                    .amount(null).build();

            assertThatThrownBy(
                    () -> walletService.createOrder(req, testUser))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Minimum recharge amount");
        }

        @Test
        @DisplayName("Should throw on amount below ₹50")
        void shouldThrowBelowMinimum() {
            CreateOrderRequest req = CreateOrderRequest.builder()
                    .amount(new BigDecimal("49.99")).build();

            assertThatThrownBy(
                    () -> walletService.createOrder(req, testUser))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Minimum recharge amount");
            verifyNoInteractions(razorpayService);
        }
    }

    // ===========================================================
    //  failPayment
    // ===========================================================

    @Nested
    @DisplayName("failPayment()")
    class FailPayment {

        @Test
        @DisplayName("Should mark PENDING as FAILED")
        void shouldMarkPendingAsFailed() {
            Wallet wallet = walletWithBalance("0.00");
            Transaction txn = pendingTx(wallet, "order_F");
            when(transactionRepository.findByOrderId("order_F"))
                    .thenReturn(Optional.of(txn));

            walletService.failPayment("order_F", testUser);

            assertThat(txn.getStatus()).isEqualTo(TransactionStatus.FAILED);
            verify(transactionRepository).save(txn);
        }

        @Test
        @DisplayName("Should no-op when already SUCCESS")
        void shouldNoOpOnSuccess() {
            Wallet wallet = walletWithBalance("0.00");
            Transaction txn = Transaction.builder().id(99L).wallet(wallet)
                    .status(TransactionStatus.SUCCESS)
                    .orderId("order_OK").build();
            when(transactionRepository.findByOrderId("order_OK"))
                    .thenReturn(Optional.of(txn));

            walletService.failPayment("order_OK", testUser);

            assertThat(txn.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
            verify(transactionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw when no tx found")
        void shouldThrowWhenNotFound() {
            when(transactionRepository.findByOrderId("no_such"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(
                    () -> walletService.failPayment("no_such", testUser))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("No transaction found");
        }

        @Test
        @DisplayName("Should throw when tx does not belong to user")
        void shouldThrowWrongUser() {
            User other = User.builder().id(999L).email("other-example-com")
                    .name("Other").build();
            Wallet otherWallet = Wallet.builder().user(other)
                    .balance(BigDecimal.ZERO).build();
            Transaction txn = pendingTx(otherWallet, "order_WRONG");
            when(transactionRepository.findByOrderId("order_WRONG"))
                    .thenReturn(Optional.of(txn));

            assertThatThrownBy(
                    () -> walletService.failPayment("order_WRONG", testUser))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("does not belong");
        }
    }

    // ===========================================================
    //  createWallet
    // ===========================================================

    @Nested
    @DisplayName("createWallet()")
    class CreateWallet {

        @Test
        @DisplayName("Should save wallet with zero balance")
        void shouldSaveZeroBalance() {
            ArgumentCaptor<Wallet> captor =
                    ArgumentCaptor.forClass(Wallet.class);

            walletService.createWallet(testUser);

            verify(walletRepository).save(captor.capture());
            Wallet w = captor.getValue();
            assertThat(w.getUser()).isEqualTo(testUser);
            assertThat(w.getBalance())
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    // ===========================================================
    //  processTransaction — RECHARGE
    // ===========================================================

    @Nested
    @DisplayName("processTransaction() — RECHARGE")
    class ProcessRecharge {

        @Test
        @DisplayName("Should throw when orderId/paymentId/signature is null")
        void shouldThrowMissingFields() {
            TransactionRequest req = new TransactionRequest();
            req.setType(TransactionType.RECHARGE);

            assertThatThrownBy(
                    () -> walletService.processTransaction(req, testUser))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(
                            "RECHARGE requires orderId, paymentId, and signature");
        }

        @Test
        @DisplayName("Should throw when payment not captured")
        void shouldThrowNonCaptured() {
            TransactionRequest req = rechargeReq(
                    "order_A", "pay_A", "sig_A");
            JSONObject payment = new JSONObject();
            payment.put("status", "failed");

            when(razorpayService.fetchPayment("pay_A"))
                    .thenReturn(payment);

            assertThatThrownBy(
                    () -> walletService.processTransaction(req, testUser))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Payment not captured");

            verify(razorpayService)
                    .verifySignature("order_A", "pay_A", "sig_A");
        }

        @Test
        @DisplayName("Should throw when amount from paise < ₹50")
        void shouldThrowAmountTooLow() {
            TransactionRequest req = rechargeReq(
                    "order_B", "pay_B", "sig_B");
            JSONObject payment = capturedPayment(3000L); // ₹30

            when(razorpayService.fetchPayment("pay_B"))
                    .thenReturn(payment);

            assertThatThrownBy(
                    () -> walletService.processTransaction(req, testUser))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Minimum recharge amount");
        }

        @Test
        @DisplayName("Should throw on replay — paymentId already SUCCESS")
        void shouldThrowReplay() {
            TransactionRequest req = rechargeReq(
                    "order_C", "pay_DUP", "sig_C");
            JSONObject payment = capturedPayment(50000L);
            Transaction existing = Transaction.builder()
                    .status(TransactionStatus.SUCCESS)
                    .paymentId("pay_DUP").build();

            when(razorpayService.fetchPayment("pay_DUP"))
                    .thenReturn(payment);
            when(transactionRepository.findByPaymentId("pay_DUP"))
                    .thenReturn(Optional.of(existing));

            assertThatThrownBy(
                    () -> walletService.processTransaction(req, testUser))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Payment already processed");
        }

        @Test
        @DisplayName("Should throw when no PENDING tx for orderId")
        void shouldThrowNoPendingTx() {
            TransactionRequest req = rechargeReq(
                    "order_GONE", "pay_G", "sig_G");
            JSONObject payment = capturedPayment(50000L);

            when(razorpayService.fetchPayment("pay_G"))
                    .thenReturn(payment);
            when(transactionRepository.findByPaymentId("pay_G"))
                    .thenReturn(Optional.empty());
            when(transactionRepository.findByOrderId("order_GONE"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(
                    () -> walletService.processTransaction(req, testUser))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("No pending transaction found");
        }

        @Test
        @DisplayName("Should throw when order tx is already SUCCESS")
        void shouldThrowOrderAlreadySuccess() {
            TransactionRequest req = rechargeReq(
                    "order_OK", "pay_J", "sig_J");
            JSONObject payment = capturedPayment(50000L);
            Transaction done = Transaction.builder().id(200L)
                    .wallet(walletWithBalance("500.00"))
                    .status(TransactionStatus.SUCCESS)
                    .orderId("order_OK").build();

            when(razorpayService.fetchPayment("pay_J"))
                    .thenReturn(payment);
            when(transactionRepository.findByPaymentId("pay_J"))
                    .thenReturn(Optional.empty());
            when(transactionRepository.findByOrderId("order_OK"))
                    .thenReturn(Optional.of(done));

            assertThatThrownBy(
                    () -> walletService.processTransaction(req, testUser))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Order already credited");
        }

        @Test
        @DisplayName("Should credit wallet, mark SUCCESS, send email")
        void shouldCompleteRecharge() {
            TransactionRequest req = rechargeReq(
                    "order_GOOD", "pay_GOOD", "sig_GOOD");
            JSONObject payment = capturedPayment(50000L); // ₹500
            Wallet wallet = walletWithBalance("100.00");
            Transaction txn = pendingTx(wallet, "order_GOOD");

            when(razorpayService.fetchPayment("pay_GOOD"))
                    .thenReturn(payment);
            when(transactionRepository.findByPaymentId("pay_GOOD"))
                    .thenReturn(Optional.empty());
            when(transactionRepository.findByOrderId("order_GOOD"))
                    .thenReturn(Optional.of(txn));
            // The code does: transaction = transactionRepository.save(transaction)
            // Must return non-null so downstream getId() works
            when(transactionRepository.save(txn)).thenReturn(txn);
            // Code re-reads wallet via findByUser for the response
            when(walletRepository.findByUser(testUser))
                    .thenReturn(Optional.of(wallet));

            TransactionResponse resp =
                    walletService.processTransaction(req, testUser);

            assertThat(wallet.getBalance())
                    .isEqualByComparingTo(new BigDecimal("600.00"));
            assertThat(txn.getStatus())
                    .isEqualTo(TransactionStatus.SUCCESS);
            assertThat(txn.getPaymentId()).isEqualTo("pay_GOOD");

            verify(walletRepository).save(wallet);

            assertThat(resp.getStatus()).isEqualTo("SUCCESS");
            assertThat(resp.getBalanceAfter())
                    .isEqualByComparingTo(new BigDecimal("600.00"));

            verify(emailService).sendRechargeConfirmation(
                    eq("test-example-com"), eq("Test User"),
                    eq(new BigDecimal("500.00")),
                    eq(new BigDecimal("600.00")));
        }
    }

    // ===========================================================
    //  processTransaction — USAGE_CHARGE
    // ===========================================================

    @Nested
    @DisplayName("processTransaction() — USAGE_CHARGE")
    class ProcessUsageCharge {

        private TransactionRequest usageReq(String amount) {
            TransactionRequest r = new TransactionRequest();
            r.setType(TransactionType.USAGE_CHARGE);
            r.setAmount(new BigDecimal(amount));
            return r;
        }

        private Wallet wallet;

        @BeforeEach
        void stubWallet() {
            wallet = walletWithBalance("100.00");
            lenient().when(walletRepository.findByUser(any(User.class)))
                    .thenReturn(Optional.of(wallet));
            lenient().when(walletRepository.findByUserForUpdate(any(User.class)))
                    .thenReturn(Optional.of(wallet));
        }

        @Test
        @DisplayName("Should throw when amount is null")
        void shouldThrowNullAmount() {
            TransactionRequest req = new TransactionRequest();
            req.setType(TransactionType.USAGE_CHARGE);
            req.setAmount(null);

            assertThatThrownBy(
                    () -> walletService.processTransaction(req, testUser))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Amount must be greater than zero");
        }

        @Test
        @DisplayName("Should throw when amount is zero")
        void shouldThrowZeroAmount() {
            TransactionRequest req = usageReq("0.00");

            assertThatThrownBy(
                    () -> walletService.processTransaction(req, testUser))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Amount must be greater than zero");
        }

        @Test
        @DisplayName("Should throw when amount is negative")
        void shouldThrowNegativeAmount() {
            TransactionRequest req = usageReq("-5.00");

            assertThatThrownBy(
                    () -> walletService.processTransaction(req, testUser))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Amount must be greater than zero");
        }

        @Test
        @DisplayName("Should throw Insufficient balance")
        void shouldThrowInsufficient() {
            Wallet poorWallet = walletWithBalance("30.00");
            when(walletRepository.findByUser(any(User.class)))
                    .thenReturn(Optional.of(poorWallet));
            when(walletRepository.findByUserForUpdate(any(User.class)))
                    .thenReturn(Optional.of(poorWallet));

            assertThatThrownBy(
                    () -> walletService.processTransaction(
                            usageReq("50.00"), testUser))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Insufficient balance");
        }

        @Test
        @DisplayName("Should use pessimistic lock for debit")
        void shouldUsePessimisticLock() {
            when(transactionRepository.save(any(Transaction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            walletService.processTransaction(usageReq("10.00"), testUser);

            verify(walletRepository).findByUserForUpdate(any(User.class));
        }

        @Test
        @DisplayName("Should subtract amount, save wallet, persist SUCCESS tx")
        void shouldSubtractAndSave() {
            when(transactionRepository.save(any(Transaction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            TransactionResponse resp =
                    walletService.processTransaction(
                            usageReq("35.50"), testUser);

            assertThat(wallet.getBalance())
                    .isEqualByComparingTo(new BigDecimal("64.50"));
            verify(walletRepository).save(wallet);

            ArgumentCaptor<Transaction> captor =
                    ArgumentCaptor.forClass(Transaction.class);
            verify(transactionRepository).save(captor.capture());
            Transaction t = captor.getValue();
            assertThat(t.getType())
                    .isEqualTo(TransactionType.USAGE_CHARGE);
            assertThat(t.getStatus())
                    .isEqualTo(TransactionStatus.SUCCESS);
            assertThat(t.getAmount())
                    .isEqualByComparingTo(new BigDecimal("35.50"));

            assertThat(resp.getStatus()).isEqualTo("SUCCESS");
            assertThat(resp.getBalanceAfter())
                    .isEqualByComparingTo(new BigDecimal("64.50"));
        }
    }

    // ===========================================================
    //  processTransaction — REFUND
    // ===========================================================

    @Nested
    @DisplayName("processTransaction() — REFUND")
    class ProcessRefund {

        private TransactionRequest refundReq(String amount) {
            TransactionRequest r = new TransactionRequest();
            r.setType(TransactionType.REFUND);
            r.setAmount(new BigDecimal(amount));
            return r;
        }

        @Test
        @DisplayName("Should add amount to balance")
        void shouldAddToBalance() {
            TransactionRequest req = refundReq("50.00");
            Wallet wallet = walletWithBalance("100.00");
            when(walletRepository.findByUser(testUser))
                    .thenReturn(Optional.of(wallet));
            Transaction saved = Transaction.builder().id(9L).build();
            when(transactionRepository.save(any(Transaction.class)))
                    .thenReturn(saved);

            TransactionResponse resp =
                    walletService.processTransaction(req, testUser);

            assertThat(wallet.getBalance())
                    .isEqualByComparingTo(new BigDecimal("150.00"));
            verify(walletRepository).save(wallet);
            assertThat(resp.getStatus()).isEqualTo("SUCCESS");
        }

        @Test
        @DisplayName("Should persist REFUND SUCCESS tx")
        void shouldPersistRefund() {
            TransactionRequest req = refundReq("25.00");
            Wallet wallet = walletWithBalance("50.00");
            when(walletRepository.findByUser(testUser))
                    .thenReturn(Optional.of(wallet));
            Transaction saved = Transaction.builder().id(11L).build();
            when(transactionRepository.save(any(Transaction.class)))
                    .thenReturn(saved);

            walletService.processTransaction(req, testUser);

            ArgumentCaptor<Transaction> captor =
                    ArgumentCaptor.forClass(Transaction.class);
            verify(transactionRepository).save(captor.capture());
            Transaction t = captor.getValue();
            assertThat(t.getType()).isEqualTo(TransactionType.REFUND);
            assertThat(t.getStatus())
                    .isEqualTo(TransactionStatus.SUCCESS);
            assertThat(t.getAmount())
                    .isEqualByComparingTo(new BigDecimal("25.00"));
        }
    }

    // ===========================================================
    //  processTransaction — ADJUSTMENT
    // ===========================================================

    @Nested
    @DisplayName("processTransaction() — ADJUSTMENT")
    class ProcessAdjustment {

        @Test
        @DisplayName("Should throw UnsupportedOperationException")
        void shouldThrowUnsupported() {
            TransactionRequest req = new TransactionRequest();
            req.setType(TransactionType.ADJUSTMENT);
            req.setAmount(new BigDecimal("100.00"));
            when(walletRepository.findByUser(testUser))
                    .thenReturn(Optional.of(walletWithBalance("500.00")));

            assertThatThrownBy(
                    () -> walletService.processTransaction(req, testUser))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("ADJUSTMENT");
        }
    }
}
