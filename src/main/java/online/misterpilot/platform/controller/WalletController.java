package online.misterpilot.platform.controller;

import online.misterpilot.platform.dto.request.CreateOrderRequest;
import online.misterpilot.platform.dto.request.FailPaymentRequest;
import online.misterpilot.platform.dto.response.CreateOrderResponse;
import online.misterpilot.platform.entity.User;
import online.misterpilot.platform.service.WalletService;
import online.misterpilot.platform.util.AuthUtil;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;
    private final AuthUtil authUtil;

    /**
     * Creates a Razorpay order for wallet top-up.
     * Frontend uses the returned orderId + keyId to open Razorpay checkout.
     */
    @CrossOrigin(origins ="*")
    @PostMapping("/create-order")
    public ResponseEntity<CreateOrderResponse> createOrder(
            @RequestBody CreateOrderRequest request) {

        User user = authUtil.getCurrentUser();
        return ResponseEntity.ok(walletService.createOrder(request, user));
    }

    @PostMapping("/fail-payment")
    public ResponseEntity<Map<String, String>> failPayment(
            @RequestBody FailPaymentRequest request) {

        User user = authUtil.getCurrentUser();
        walletService.failPayment(request.getOrderId(), user);
        return ResponseEntity.ok(Map.of("message", "Payment marked as failed"));
    }
}
