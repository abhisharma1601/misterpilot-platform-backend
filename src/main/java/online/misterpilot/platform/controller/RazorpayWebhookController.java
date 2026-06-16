package online.misterpilot.platform.controller;

import online.misterpilot.platform.service.RazorpayService;
import online.misterpilot.platform.service.WalletService;

import org.json.JSONObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/webhook")
@RequiredArgsConstructor
public class RazorpayWebhookController {

    private final RazorpayService razorpayService;
    private final WalletService walletService;

    @PostMapping("/razorpay")
    public ResponseEntity<String> handleWebhook(
            @RequestHeader("X-Razorpay-Signature") String signature,
            @RequestBody String payload) {

        razorpayService.verifyWebhookSignature(payload, signature);

        JSONObject body = new JSONObject(payload);
        String event = body.optString("event");

        JSONObject paymentEntity = body
                .getJSONObject("payload")
                .getJSONObject("payment")
                .getJSONObject("entity");

        String orderId  = paymentEntity.getString("order_id");
        String paymentId = paymentEntity.getString("id");

        switch (event) {
            case "payment.captured" -> {
                long amountPaise = paymentEntity.getLong("amount");
                log.info("Webhook payment.captured: orderId={}, paymentId={}, amountPaise={}",
                        orderId, paymentId, amountPaise);
                walletService.webhookCreditWallet(orderId, paymentId, amountPaise);
            }
            case "payment.failed" -> {
                log.info("Webhook payment.failed: orderId={}, paymentId={}", orderId, paymentId);
                walletService.webhookFailPayment(orderId, paymentId);
            }
            default -> log.warn("Webhook: unhandled event type={}", event);
        }

        return ResponseEntity.ok("OK");
    }
}
