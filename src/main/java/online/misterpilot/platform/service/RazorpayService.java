package online.misterpilot.platform.service;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import online.misterpilot.platform.config.RazorpayProperties;
import lombok.RequiredArgsConstructor;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RazorpayService {

    private final RazorpayClient razorpayClient;
    private final RazorpayProperties razorpayProperties;

    public JSONObject createOrder(Long amount) {
        try {
            JSONObject options = new JSONObject();
            options.put("amount", amount * 100); // Convert ₹ to paise
            options.put("currency", "INR");
            options.put("receipt", "receipt_" + System.currentTimeMillis());
            Order order = razorpayClient.orders.create(options);
            return order.toJson();
        } catch (Exception ex) {
            throw new RuntimeException("Failed to create Razorpay order", ex);
        }
    }

    public JSONObject fetchPayment(String paymentId) {
        try {
            com.razorpay.Payment payment = razorpayClient.payments.fetch(paymentId);
            return payment.toJson();
        } catch (Exception ex) {
            throw new RuntimeException("Failed to fetch Razorpay payment: " + paymentId, ex);
        }
    }

    /**
     * Verifies the Razorpay webhook signature (HMAC-SHA256 of raw body).
     * Throws RuntimeException if signature is invalid.
     */
    public void verifyWebhookSignature(String payload, String signature) {
        try {
            Utils.verifyWebhookSignature(payload, signature, razorpayProperties.getWebhookSecret());
        } catch (RazorpayException ex) {
            throw new RuntimeException("Webhook signature verification failed", ex);
        }
    }

    /**
     * Verifies the Razorpay payment signature.
     * Throws RuntimeException if signature is invalid.
     */
    public void verifySignature(String orderId, String paymentId, String signature) {
        try {
            JSONObject attributes = new JSONObject();
            attributes.put("razorpay_order_id", orderId);
            attributes.put("razorpay_payment_id", paymentId);
            attributes.put("razorpay_signature", signature);
            Utils.verifyPaymentSignature(attributes, razorpayProperties.getKeySecret());
        } catch (RazorpayException ex) {
            throw new RuntimeException("Payment signature verification failed", ex);
        }
    }
}
