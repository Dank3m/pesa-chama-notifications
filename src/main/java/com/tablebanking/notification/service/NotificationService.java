package com.tablebanking.notification.service;

import com.tablebanking.notification.dto.*;
import com.tablebanking.notification.entity.*;
import com.tablebanking.notification.entity.enums.*;
import com.tablebanking.notification.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationLogRepository notificationLogRepository;
    private final NotificationTemplateRepository templateRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final SmsService smsService;
    private final EmailService emailService;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${notification.deduplication.enabled:true}")
    private boolean deduplicationEnabled;

    @Value("${notification.deduplication.ttl-minutes:60}")
    private int deduplicationTtlMinutes;

    /**
     * Process a generic notification event
     */
    @Transactional
    public NotificationResponse processNotificationEvent(NotificationEvent event) {
        log.info("Processing notification event: {} for member: {}", event.getEventType(), event.getMemberId());

        // Check for duplicate
        if (deduplicationEnabled && isDuplicateEvent(event.getEventId())) {
            log.warn("Duplicate event detected: {}", event.getEventId());
            return NotificationResponse.builder()
                    .status("DUPLICATE")
                    .message("Event already processed")
                    .build();
        }

        // Determine template based on event type
        String templateCode = mapEventTypeToTemplate(event.getEventType());
        
        // Build variables from event data
        Map<String, Object> variables = buildVariables(event);

        // Send via appropriate channels
        NotificationResponse response = null;
        
        // Try SMS first if phone number available
        if (event.getPhoneNumber() != null && !event.getPhoneNumber().isBlank()) {
            response = sendSmsNotification(event, templateCode, variables);
        }
        
        // Also send email if available
        if (event.getEmail() != null && !event.getEmail().isBlank()) {
            sendEmailNotification(event, templateCode + "_EMAIL", variables);
        }

        // Mark event as processed for deduplication
        if (deduplicationEnabled) {
            markEventProcessed(event.getEventId());
        }

        return response != null ? response : NotificationResponse.builder()
                .status("NO_CHANNEL")
                .message("No notification channel available")
                .build();
    }

    /**
     * Process contribution event
     */
    @Transactional
    public NotificationResponse processContributionEvent(ContributionEvent event) {
        log.info("Processing contribution event: {} for member: {}", event.getEventType(), event.getMemberName());

        NotificationEvent notificationEvent = NotificationEvent.builder()
                .eventId(event.getEventId())
                .eventType(event.getEventType())
                .memberId(event.getMemberId())
                .memberName(event.getMemberName())
                .phoneNumber(event.getPhoneNumber())
                .email(event.getEmail())
                .groupId(event.getGroupId())
                .groupName(event.getGroupName())
                .data(buildContributionData(event))
                .timestamp(event.getTimestamp())
                .build();

        return processNotificationEvent(notificationEvent);
    }

    /**
     * Process loan event
     */
    @Transactional
    public NotificationResponse processLoanEvent(LoanEvent event) {
        log.info("Processing loan event: {} for member: {}", event.getEventType(), event.getMemberName());

        NotificationEvent notificationEvent = NotificationEvent.builder()
                .eventId(event.getEventId())
                .eventType(event.getEventType())
                .memberId(event.getMemberId())
                .memberName(event.getMemberName())
                .phoneNumber(event.getPhoneNumber())
                .email(event.getEmail())
                .groupId(event.getGroupId())
                .groupName(event.getGroupName())
                .data(buildLoanData(event))
                .timestamp(event.getTimestamp())
                .build();

        return processNotificationEvent(notificationEvent);
    }

    /**
     * Send SMS notification
     */
    private NotificationResponse sendSmsNotification(NotificationEvent event, String templateCode, 
                                                      Map<String, Object> variables) {
        // Check member preferences
        if (!isNotificationEnabled(event.getMemberId(), NotificationChannel.SMS, event.getEventType())) {
            log.info("SMS notifications disabled for member: {} event: {}", event.getMemberId(), event.getEventType());
            return NotificationResponse.builder()
                    .status("DISABLED")
                    .message("SMS notifications disabled by user")
                    .build();
        }

        // Get template
        String message = buildMessageFromTemplate(templateCode, variables);

        // Create notification log
        NotificationLog notificationLog = NotificationLog.builder()
                .eventId(event.getEventId())
                .eventType(event.getEventType())
                .memberId(event.getMemberId())
                .memberName(event.getMemberName())
                .groupId(event.getGroupId())
                .channel(NotificationChannel.SMS)
                .recipient(event.getPhoneNumber())
                .templateCode(templateCode)
                .message(message)
                .variables(variables)
                .status(NotificationStatus.PENDING)
                .build();
        
        notificationLog = notificationLogRepository.save(notificationLog);

        // Send via SMS service
        try {
            SmsService.SmsResult result = smsService.sendSms(event.getPhoneNumber(), message);
            
            notificationLog.setStatus(result.isSuccess() ? NotificationStatus.SENT : NotificationStatus.FAILED);
            notificationLog.setProviderMessageId(result.getMessageId());
            notificationLog.setProvider(result.getProvider());
            notificationLog.setSentAt(Instant.now());
            
            if (!result.isSuccess()) {
                notificationLog.setErrorMessage(result.getErrorMessage());
            }
            
            notificationLogRepository.save(notificationLog);

            return NotificationResponse.builder()
                    .notificationId(notificationLog.getId())
                    .status(notificationLog.getStatus().name())
                    .message(result.isSuccess() ? "SMS sent successfully" : result.getErrorMessage())
                    .providerMessageId(result.getMessageId())
                    .sentAt(notificationLog.getSentAt())
                    .build();
                    
        } catch (Exception e) {
            log.error("Failed to send SMS to {}: {}", event.getPhoneNumber(), e.getMessage());
            notificationLog.setStatus(NotificationStatus.FAILED);
            notificationLog.setErrorMessage(e.getMessage());
            notificationLogRepository.save(notificationLog);

            return NotificationResponse.builder()
                    .notificationId(notificationLog.getId())
                    .status("FAILED")
                    .message(e.getMessage())
                    .build();
        }
    }

    /**
     * Send email notification asynchronously
     */
    @Async
    public CompletableFuture<NotificationResponse> sendEmailNotification(NotificationEvent event, 
                                                                          String templateCode, 
                                                                          Map<String, Object> variables) {
        // Check member preferences
        if (!isNotificationEnabled(event.getMemberId(), NotificationChannel.EMAIL, event.getEventType())) {
            log.info("Email notifications disabled for member: {}", event.getMemberId());
            return CompletableFuture.completedFuture(NotificationResponse.builder()
                    .status("DISABLED")
                    .message("Email notifications disabled by user")
                    .build());
        }

        Optional<NotificationTemplate> templateOpt = templateRepository.findByTemplateCode(templateCode);
        if (templateOpt.isEmpty()) {
            // Try non-email version
            templateOpt = templateRepository.findByTemplateCode(templateCode.replace("_EMAIL", ""));
        }
        
        if (templateOpt.isEmpty()) {
            log.warn("Email template not found: {}", templateCode);
            return CompletableFuture.completedFuture(NotificationResponse.builder()
                    .status("FAILED")
                    .message("Template not found")
                    .build());
        }

        NotificationTemplate template = templateOpt.get();
        String subject = substituteVariables(template.getSubjectTemplate(), variables);
        String body = substituteVariables(template.getBodyTemplate(), variables);

        // Create notification log
        NotificationLog notificationLog = NotificationLog.builder()
                .eventId(event.getEventId() + "_EMAIL")
                .eventType(event.getEventType())
                .memberId(event.getMemberId())
                .memberName(event.getMemberName())
                .groupId(event.getGroupId())
                .channel(NotificationChannel.EMAIL)
                .recipient(event.getEmail())
                .templateCode(templateCode)
                .subject(subject)
                .message(body)
                .variables(variables)
                .status(NotificationStatus.PENDING)
                .build();
        
        notificationLog = notificationLogRepository.save(notificationLog);

        try {
            emailService.sendEmail(event.getEmail(), subject, body);
            
            notificationLog.setStatus(NotificationStatus.SENT);
            notificationLog.setSentAt(Instant.now());
            notificationLogRepository.save(notificationLog);

            return CompletableFuture.completedFuture(NotificationResponse.builder()
                    .notificationId(notificationLog.getId())
                    .status("SENT")
                    .message("Email sent successfully")
                    .sentAt(notificationLog.getSentAt())
                    .build());
                    
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", event.getEmail(), e.getMessage());
            notificationLog.setStatus(NotificationStatus.FAILED);
            notificationLog.setErrorMessage(e.getMessage());
            notificationLogRepository.save(notificationLog);

            return CompletableFuture.completedFuture(NotificationResponse.builder()
                    .notificationId(notificationLog.getId())
                    .status("FAILED")
                    .message(e.getMessage())
                    .build());
        }
    }

    private String buildMessageFromTemplate(String templateCode, Map<String, Object> variables) {
        Optional<NotificationTemplate> templateOpt = templateRepository.findByTemplateCode(templateCode);
        
        if (templateOpt.isPresent()) {
            return substituteVariables(templateOpt.get().getBodyTemplate(), variables);
        }
        
        // Fallback to default message
        return "Notification from Table Banking Group";
    }

    private String substituteVariables(String template, Map<String, Object> variables) {
        if (template == null) return "";
        
        String result = template;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String placeholder = "{" + entry.getKey() + "}";
            String value = entry.getValue() != null ? entry.getValue().toString() : "";
            result = result.replace(placeholder, value);
        }
        return result;
    }

    private String mapEventTypeToTemplate(String eventType) {
        return switch (eventType) {
            case "CONTRIBUTION_RECEIVED" -> "CONTRIBUTION_RECEIVED";
            case "CONTRIBUTION_REMINDER" -> "CONTRIBUTION_REMINDER";
            case "CONTRIBUTION_DEFAULTED", "CONTRIBUTION_OVERDUE" -> "CONTRIBUTION_OVERDUE";
            case "LOAN_APPROVED" -> "LOAN_APPROVED";
            case "LOAN_DISBURSED" -> "LOAN_DISBURSED";
            case "LOAN_PAYMENT_REMINDER" -> "LOAN_PAYMENT_REMINDER";
            case "LOAN_PAYMENT_RECEIVED" -> "LOAN_PAYMENT_RECEIVED";
            case "LOAN_OVERDUE" -> "LOAN_OVERDUE";
            case "LOAN_CREATED_FROM_DEFAULT" -> "DEFAULT_CONVERTED";
            default -> "GENERAL";
        };
    }

    private Map<String, Object> buildVariables(NotificationEvent event) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("memberName", event.getMemberName());
        variables.put("groupName", event.getGroupName());
        
        if (event.getData() != null) {
            variables.putAll(event.getData());
        }
        
        return variables;
    }

    private Map<String, Object> buildContributionData(ContributionEvent event) {
        Map<String, Object> data = new HashMap<>();
        data.put("amount", formatAmount(event.getExpectedAmount()));
        data.put("paidAmount", formatAmount(event.getPaidAmount()));
        data.put("month", formatMonth(event.getCycleMonth()));
        data.put("dueDate", formatDate(event.getDueDate()));
        return data;
    }

    private Map<String, Object> buildLoanData(LoanEvent event) {
        Map<String, Object> data = new HashMap<>();
        data.put("amount", formatAmount(event.getAmount()));
        data.put("outstanding", formatAmount(event.getOutstandingBalance()));
        data.put("loanNumber", event.getLoanNumber());
        data.put("dueDate", formatDate(event.getDueDate()));
        data.put("startDate", formatDate(event.getDisbursementDate()));
        return data;
    }

    private String formatAmount(java.math.BigDecimal amount) {
        if (amount == null) return "0";
        return String.format("%,.2f", amount);
    }

    private String formatMonth(LocalDate date) {
        if (date == null) return "";
        return date.format(DateTimeFormatter.ofPattern("MMMM yyyy"));
    }

    private String formatDate(LocalDate date) {
        if (date == null) return "";
        return date.format(DateTimeFormatter.ofPattern("dd MMM yyyy"));
    }

    private boolean isNotificationEnabled(UUID memberId, NotificationChannel channel, String eventType) {
        return preferenceRepository.isNotificationEnabled(memberId, channel, eventType);
    }

    private boolean isDuplicateEvent(String eventId) {
        if (eventId == null) return false;
        String key = "notification:event:" + eventId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    private void markEventProcessed(String eventId) {
        if (eventId == null) return;
        String key = "notification:event:" + eventId;
        redisTemplate.opsForValue().set(key, "processed", Duration.ofMinutes(deduplicationTtlMinutes));
    }

    /**
     * Update delivery status from provider callback
     */
    @Transactional
    public void updateDeliveryStatus(String providerMessageId, String status, String description) {
        notificationLogRepository.findByProviderMessageId(providerMessageId)
                .ifPresent(notification -> {
                    NotificationStatus newStatus = mapProviderStatus(status);
                    notification.setStatus(newStatus);
                    
                    if (newStatus == NotificationStatus.DELIVERED) {
                        notification.setDeliveredAt(Instant.now());
                    }
                    
                    notificationLogRepository.save(notification);
                    log.info("Updated delivery status for message {}: {}", providerMessageId, status);
                });
    }

    private NotificationStatus mapProviderStatus(String providerStatus) {
        return switch (providerStatus.toUpperCase()) {
            case "DELIVERED", "DELIVERED_TO_HANDSET" -> NotificationStatus.DELIVERED;
            case "SENT", "PENDING_DELIVERED" -> NotificationStatus.SENT;
            case "FAILED", "REJECTED", "UNDELIVERABLE" -> NotificationStatus.FAILED;
            default -> NotificationStatus.SENT;
        };
    }

    /**
     * Get notification statistics
     */
    public NotificationStats getStats() {
        long totalSent = notificationLogRepository.countByStatus(NotificationStatus.SENT) +
                         notificationLogRepository.countByStatus(NotificationStatus.DELIVERED);
        long totalDelivered = notificationLogRepository.countByStatus(NotificationStatus.DELIVERED);
        long totalFailed = notificationLogRepository.countByStatus(NotificationStatus.FAILED);
        long totalPending = notificationLogRepository.countByStatus(NotificationStatus.PENDING);

        double deliveryRate = totalSent > 0 ? (double) totalDelivered / totalSent * 100 : 0;

        Map<String, Long> byChannel = new HashMap<>();
        byChannel.put("SMS", notificationLogRepository.countByChannelAndStatus(NotificationChannel.SMS, NotificationStatus.SENT));
        byChannel.put("EMAIL", notificationLogRepository.countByChannelAndStatus(NotificationChannel.EMAIL, NotificationStatus.SENT));

        return NotificationStats.builder()
                .totalSent(totalSent)
                .totalDelivered(totalDelivered)
                .totalFailed(totalFailed)
                .totalPending(totalPending)
                .deliveryRate(deliveryRate)
                .byChannel(byChannel)
                .build();
    }
}
