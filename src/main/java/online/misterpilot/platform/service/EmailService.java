package online.misterpilot.platform.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String fromAddress;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Async
    public void sendRechargeConfirmation(String toEmail, String name, BigDecimal amount, BigDecimal newBalance) {
        String subject = "Wallet Recharged — ₹" + amount.toPlainString();
        String body = """
                <html><body style="font-family:sans-serif;color:#222;max-width:480px;margin:auto">
                  <h2>Wallet Recharged ✓</h2>
                  <p>Hi %s,</p>
                  <p>Your MisterPilot wallet has been topped up successfully.</p>
                  <table style="border-collapse:collapse;width:100%%">
                    <tr>
                      <td style="padding:8px;border:1px solid #ddd">Amount Added</td>
                      <td style="padding:8px;border:1px solid #ddd"><strong>₹%s</strong></td>
                    </tr>
                    <tr>
                      <td style="padding:8px;border:1px solid #ddd">New Balance</td>
                      <td style="padding:8px;border:1px solid #ddd"><strong>₹%s</strong></td>
                    </tr>
                    <tr>
                      <td style="padding:8px;border:1px solid #ddd">Date</td>
                      <td style="padding:8px;border:1px solid #ddd">%s</td>
                    </tr>
                  </table>
                  <p style="margin-top:24px;color:#888;font-size:12px">MisterPilot Platform</p>
                </body></html>
                """.formatted(
                name,
                amount.toPlainString(),
                newBalance.toPlainString(),
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a"))
        );

        send(toEmail, subject, body);
    }

    @Async
    public void sendPasswordResetLink(String toEmail, String name, String token) {
        String resetUrl = frontendUrl + "/reset-password?token=" + token;
        String subject = "Reset Your MisterPilot Password";
        String body = """
                <html><body style="font-family:sans-serif;color:#222;max-width:480px;margin:auto">
                  <h2>Password Reset Request</h2>
                  <p>Hi %s,</p>
                  <p>Click the button below to reset your password. This link expires in <strong>15 minutes</strong>.</p>
                  <a href="%s" style="display:inline-block;margin:16px 0;padding:12px 24px;background:#000;color:#fff;text-decoration:none;border-radius:6px">
                    Reset Password
                  </a>
                  <p>If you didn't request this, ignore this email — your password won't change.</p>
                  <p style="margin-top:24px;color:#888;font-size:12px">MisterPilot Platform</p>
                </body></html>
                """.formatted(name, resetUrl);

        send(toEmail, subject, body);
    }

    @Async
    public void sendEmailVerificationLink(String toEmail, String name, String token) {
        String verifyUrl = frontendUrl + "/verify-email?token=" + token;
        String subject = "Verify Your MisterPilot Email";
        String body = """
                <html><body style="font-family:sans-serif;color:#222;max-width:480px;margin:auto">
                  <h2>Confirm Your Email</h2>
                  <p>Hi %s,</p>
                  <p>Thanks for signing up for MisterPilot. Click the button below to activate your
                     account. This link expires in <strong>24 hours</strong>.</p>
                  <a href="%s" style="display:inline-block;margin:16px 0;padding:12px 24px;background:#000;color:#fff;text-decoration:none;border-radius:6px">
                    Verify Email
                  </a>
                  <p>If you didn't create a MisterPilot account, you can safely ignore this email.</p>
                  <p style="margin-top:24px;color:#888;font-size:12px">MisterPilot Platform</p>
                </body></html>
                """.formatted(name, verifyUrl);

        send(toEmail, subject, body);
    }

    /**
     * Onboarding email sent to every new account, regardless of signup method
     * (email/password or Google).
     */
    @Async
    public void sendWelcomeEmail(String toEmail, String name) {
        String subject = "Welcome to MisterPilot!";
        String body = """
                <html><body style="font-family:sans-serif;color:#222;max-width:480px;margin:auto">
                  <p>Hi %s,</p>
                  <p>Welcome to MisterPilot! We're excited to have you on board.</p>
                  <p>MisterPilot is designed to help developers code faster, solve problems more
                     efficiently, and stay focused by bringing AI-powered assistance directly into
                     their workflow.</p>
                  <p>To get started:</p>
                  <ol>
                    <li>Log in to your account.</li>
                    <li>Generate or manage your API keys from the dashboard.</li>
                    <li>Install the VS Code extension.</li>
                    <li>Start building with AI assistance right inside your editor.</li>
                  </ol>
                  <p>If you have any questions, feedback, or run into any issues, simply reply to
                     this email. We're always happy to help and would love to hear about your
                     experience.</p>
                  <p>Thank you for joining us and being part of the MisterPilot journey.</p>
                  <p>Happy coding!</p>
                  <p>Best regards,<br>The MisterPilot Team</p>
                  <a href="%s" style="color:#000">platform.misterpilot.online</a>
                  <p style="margin-top:24px;color:#888;font-size:12px">MisterPilot Platform</p>
                </body></html>
                """.formatted(firstName(name), frontendUrl);

        send(toEmail, subject, body);
    }

    /**
     * The greeting uses only the first word of the stored name,
     * falling back to a neutral greeting when no name is available.
     */
    private static String firstName(String name) {
        if (name == null || name.isBlank()) {
            return "there";
        }
        return name.trim().split("\\s+")[0];
    }

    private void send(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("Email sent: to={}, subject={}", to, subject);
        } catch (MessagingException e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }
}
