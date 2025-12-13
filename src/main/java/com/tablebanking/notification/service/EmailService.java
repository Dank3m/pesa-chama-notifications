package com.tablebanking.notification.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${notification.email.enabled:false}")
    private boolean emailEnabled;

    @Value("${notification.email.from-address:noreply@tablebanking.com}")
    private String fromAddress;

    @Value("${notification.email.from-name:Table Banking}")
    private String fromName;

    /**
     * Send simple text email
     */
    @Retryable(
            retryFor = {MessagingException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public void sendEmail(String to, String subject, String body) {
        if (!emailEnabled) {
            log.info("Email disabled. Would send to {}: Subject: {}", to, subject);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromName + " <" + fromAddress + ">");
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            
            mailSender.send(message);
            log.info("Email sent successfully to {}: {}", to, subject);
            
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
            throw new RuntimeException("Failed to send email", e);
        }
    }

    /**
     * Send HTML email using Thymeleaf template
     */
    @Retryable(
            retryFor = {MessagingException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public void sendHtmlEmail(String to, String subject, String templateName, Map<String, Object> variables) {
        if (!emailEnabled) {
            log.info("Email disabled. Would send HTML to {}: Subject: {}", to, subject);
            return;
        }

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            
            helper.setFrom(fromName + " <" + fromAddress + ">");
            helper.setTo(to);
            helper.setSubject(subject);
            
            // Process Thymeleaf template
            Context context = new Context();
            context.setVariables(variables);
            String htmlContent = templateEngine.process(templateName, context);
            
            helper.setText(htmlContent, true);
            
            mailSender.send(mimeMessage);
            log.info("HTML email sent successfully to {}: {}", to, subject);
            
        } catch (MessagingException e) {
            log.error("Failed to send HTML email to {}: {}", to, e.getMessage());
            throw new RuntimeException("Failed to send email", e);
        }
    }

    /**
     * Send email asynchronously
     */
    @Async
    public CompletableFuture<Boolean> sendEmailAsync(String to, String subject, String body) {
        try {
            sendEmail(to, subject, body);
            return CompletableFuture.completedFuture(true);
        } catch (Exception e) {
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Send bulk emails
     */
    @Async
    public CompletableFuture<Integer> sendBulkEmails(Map<String, String> recipients, String subject, String body) {
        int successCount = 0;
        
        for (Map.Entry<String, String> recipient : recipients.entrySet()) {
            try {
                String personalizedBody = body.replace("{memberName}", recipient.getValue());
                sendEmail(recipient.getKey(), subject, personalizedBody);
                successCount++;
            } catch (Exception e) {
                log.error("Failed to send bulk email to {}: {}", recipient.getKey(), e.getMessage());
            }
        }
        
        return CompletableFuture.completedFuture(successCount);
    }

    /**
     * Send contribution reminder email
     */
    public void sendContributionReminderEmail(String to, String memberName, String groupName, 
                                               String amount, String month, String dueDate) {
        Map<String, Object> variables = Map.of(
                "memberName", memberName,
                "groupName", groupName,
                "amount", amount,
                "month", month,
                "dueDate", dueDate
        );
        
        sendHtmlEmail(to, "Contribution Reminder - " + month, "contribution-reminder", variables);
    }

    /**
     * Send loan approval email
     */
    public void sendLoanApprovalEmail(String to, String memberName, String groupName,
                                       String amount, String loanNumber) {
        Map<String, Object> variables = Map.of(
                "memberName", memberName,
                "groupName", groupName,
                "amount", amount,
                "loanNumber", loanNumber
        );
        
        sendHtmlEmail(to, "Loan Approved - " + loanNumber, "loan-approved", variables);
    }

    /**
     * Send loan payment reminder email
     */
    public void sendLoanPaymentReminderEmail(String to, String memberName, String groupName,
                                              String amount, String outstanding, String dueDate) {
        Map<String, Object> variables = Map.of(
                "memberName", memberName,
                "groupName", groupName,
                "amount", amount,
                "outstanding", outstanding,
                "dueDate", dueDate
        );
        
        sendHtmlEmail(to, "Loan Payment Reminder", "loan-payment-reminder", variables);
    }
}
