package online.misterpilot.platform.service;

import online.misterpilot.platform.dto.request.CreateOrderRequest;
import online.misterpilot.platform.dto.request.TransactionRequest;
import online.misterpilot.platform.dto.response.CreateOrderResponse;
import online.misterpilot.platform.dto.response.TransactionResponse;
import online.misterpilot.platform.config.RazorpayProperties;
import online.misterpilot.platform.entity.*;
import online.misterpilot.platform.enums.TransactionStatus;
import online.misterpilot.platform.enums.TransactionType;
import online.misterpilot.platform.repository.TransactionRepository;
import online.misterpilot.platform.repository.WalletRepository;

import org.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final RazorpayService razorpayService;
    private final RazorpayProperties razorpayProperties;
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal MIN_RECHARGE = new BigDecimal("50");

    /**
     * Creates a Razorpay order and logs a PENDING transaction immediately.
     * Returns everything the frontend needs to open Razorpay checkout.
     */
    @Transactional
    public CreateOrderResponse createOrder(CreateOrderRequest request, User user) {

        BigDecimal amount = request.getAmount();

        if (amount == null || amount.compareTo(MIN_RECHARGE) < 0) {
            throw new IllegalArgumentException(
                    "Minimum recharge amount is ₹" + MIN_RECHARGE.toPlainString());
        }

        JSONObject order = razorpayService.createOrder(amount.longValue());
        String orderId = order.getString("id");

        Wallet wallet = walletRepository.findByUser(user)
                .orElseThrow(() -> new IllegalStateException(
                        "Wallet not found for user id=" + user.getId()));

        Transaction transaction = Transaction.builder()
                .wallet(wallet)
                .type(TransactionType.RECHARGE)
                .status(TransactionStatus.PENDING)
                .amount(amount)
                .orderId(orderId)
                .build();
        transaction = transactionRepository.save(transaction);

        log.info("Order created: orderId={}, transactionId={}, amount={}, user={}",
                orderId, transaction.getId(), amount, user.getId());

        return CreateOrderResponse.builder()
                .transactionId(transaction.getId())
                .orderId(orderId)
                .amount(amount)
                .currency(order.optString("currency", "INR"))
                .keyId(razorpayProperties.getKeyId())
                .receipt(order.optString("receipt", ""))
                .build();
    }

    /**
     * Marks a PENDING transaction as FAILED.
     * Called by the frontend when Razorpay payment fails or is dismissed.
     * No-ops silently if the transaction is already SUCCESS (payment may have gone through via webhook).
     */
    @Transactional
    public void failPayment(String orderId, User user) {
        Transaction transaction = transactionRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalStateException(
                        "No transaction found for orderId=" + orderId));

        if (!transaction.getWallet().getUser().getId().equals(user.getId())) {
            throw new IllegalStateException("Transaction does not belong to this user");
        }

        if (transaction.getStatus() == TransactionStatus.PENDING) {
            transaction.setStatus(TransactionStatus.FAILED);
            transactionRepository.save(transaction);
            log.info("Transaction marked FAILED: orderId={}, user={}", orderId, user.getId());
        }
    }

    /**
     * Called by the Razorpay webhook on payment.captured.
     * Finds the PENDING transaction by orderId, credits the wallet, and marks SUCCESS.
     * Idempotent — no-ops if already SUCCESS.
     */
    @Transactional
    public void webhookCreditWallet(String orderId, String paymentId, long amountPaise) {
        Transaction transaction = transactionRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalStateException(
                        "No transaction found for orderId=" + orderId));

        if (transaction.getStatus() == TransactionStatus.SUCCESS) {
            log.info("Webhook: already SUCCESS, skipping. orderId={}", orderId);
            return;
        }

        // Guard against duplicate paymentId already credited
        Optional<Transaction> duplicate = transactionRepository.findByPaymentId(paymentId);
        if (duplicate.isPresent() && duplicate.get().getStatus() == TransactionStatus.SUCCESS) {
            throw new IllegalStateException("Payment already processed: paymentId=" + paymentId);
        }

        BigDecimal amount = BigDecimal.valueOf(amountPaise)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        Wallet wallet = transaction.getWallet();
        wallet.setBalance(wallet.getBalance().add(amount));
        walletRepository.save(wallet);

        transaction.setStatus(TransactionStatus.SUCCESS);
        transaction.setPaymentId(paymentId);
        transaction.setAmount(amount);
        transactionRepository.save(transaction);

        log.info("Webhook: wallet credited. orderId={}, paymentId={}, amount={}, newBalance={}",
                orderId, paymentId, amount, wallet.getBalance());
    }

    /**
     * Called by the Razorpay webhook on payment.failed.
     * Finds the PENDING transaction by orderId, sets the paymentId, and marks FAILED.
     * Idempotent — no-ops if already SUCCESS or FAILED.
     */
    @Transactional
    public void webhookFailPayment(String orderId, String paymentId) {
        Transaction transaction = transactionRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalStateException(
                        "No transaction found for orderId=" + orderId));

        if (transaction.getStatus() != TransactionStatus.PENDING) {
            log.info("Webhook: transaction already {} skipping fail. orderId={}",
                    transaction.getStatus(), orderId);
            return;
        }

        transaction.setStatus(TransactionStatus.FAILED);
        transaction.setPaymentId(paymentId);
        transactionRepository.save(transaction);

        log.info("Webhook: transaction marked FAILED. orderId={}, paymentId={}", orderId, paymentId);
    }

    /**
     * Creates a new wallet for a user with zero balance.
     */
    @Transactional
    public void createWallet(User user) {
        Wallet wallet = Wallet.builder()
                .user(user)
                .balance(BigDecimal.ZERO)
                .build();
        walletRepository.save(wallet);
        log.info("Wallet created for user id={}", user.getId());
    }

    @Transactional
    public TransactionResponse processTransaction(TransactionRequest request, User user) {
        TransactionType type = request.getType();
        BigDecimal amount = request.getAmount();
        String orderId = request.getOrderId();
        String paymentId = request.getPaymentId();

        Wallet wallet = (type == TransactionType.USAGE_CHARGE
                ? walletRepository.findByUserForUpdate(user)
                : walletRepository.findByUser(user))
                .orElseThrow(() -> new IllegalStateException(
                        "Wallet not found for user id=" + user.getId()));

        if (amount == null || amount.compareTo(ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }

        switch (type) {
            case USAGE_CHARGE -> {
                if (wallet.getBalance().compareTo(amount) < 0) {
                    throw new IllegalArgumentException(
                            "Insufficient balance: required=" + amount
                            + ", available=" + wallet.getBalance());
                }
                wallet.setBalance(wallet.getBalance().subtract(amount));
                log.info("Wallet debited: user={}, amount={}, newBalance={}",
                        user.getId(), amount, wallet.getBalance());
            }
            case REFUND -> {
                wallet.setBalance(wallet.getBalance().add(amount));
                log.info("Wallet refunded: user={}, amount={}, newBalance={}",
                        user.getId(), amount, wallet.getBalance());
            }
            case ADJUSTMENT -> {
                throw new UnsupportedOperationException(
                        "ADJUSTMENT type not yet supported (admin only)");
            }
            default -> throw new IllegalArgumentException("Unhandled transaction type: " + type);
        }

        walletRepository.save(wallet);

        Transaction transaction = Transaction.builder()
                .wallet(wallet)
                .type(type)
                .status(TransactionStatus.SUCCESS)
                .orderId(orderId)
                .paymentId(paymentId)
                .amount(amount)
                .build();
        transaction = transactionRepository.save(transaction);

        log.info("Transaction saved: id={}, type={}, amount={}",
                transaction.getId(), type, amount);

        return TransactionResponse.builder()
                .transactionId(transaction.getId())
                .amount(transaction.getAmount())
                .type(type.name())
                .status(TransactionStatus.SUCCESS.name())
                .balanceAfter(wallet.getBalance())
                .createdAt(transaction.getCreatedAt())
                .message("Transaction successful")
                .build();
    }
}
