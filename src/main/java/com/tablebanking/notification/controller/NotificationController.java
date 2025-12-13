package com.tablebanking.notification.controller;

import com.tablebanking.notification.dto.*;
import com.tablebanking.notification.entity.NotificationLog;
import com.tablebanking.notification.entity.enums.NotificationStatus;
import com.tablebanking.notification.repository.NotificationLogRepository;
import com.tablebanking.notification.service.NotificationService;
import com.tablebanking.notification.service.SmsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Notifications", description = "Notification management endpoints")
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationLogRepository notificationLogRepository;
    private final SmsService smsService;

    @PostMapping("/send")
    @Operation(summary = "Send a notification manually")
    public ResponseEntity<NotificationResponse> sendNotification(@RequestBody SendNotificationRequest request) {
        log.info("Manual notification request for member: {}", request.getMemberId());
        
        NotificationEvent event = NotificationEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(request.getTemplateCode())
                .memberId(request.getMemberId())
                .memberName(request.getMemberName())
                .phoneNumber(request.getPhoneNumber())
                .email(request.getEmail())
                .groupId(request.getGroupId())
                .groupName(request.getGroupName())
                .data(request.getVariables())
                .build();
        
        NotificationResponse response = notificationService.processNotificationEvent(event);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get notification by ID")
    public ResponseEntity<NotificationLog> getNotification(@PathVariable UUID id) {
        return notificationLogRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/member/{memberId}")
    @Operation(summary = "Get notifications for a member")
    public ResponseEntity<Page<NotificationLog>> getMemberNotifications(
            @PathVariable UUID memberId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(
                notificationLogRepository.findByMemberIdOrderByCreatedAtDesc(memberId, pageable)
        );
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "Get notifications by status")
    public ResponseEntity<List<NotificationLog>> getNotificationsByStatus(
            @PathVariable NotificationStatus status) {
        return ResponseEntity.ok(notificationLogRepository.findByStatus(status));
    }

    @GetMapping("/stats")
    @Operation(summary = "Get notification statistics")
    public ResponseEntity<NotificationStats> getStats() {
        return ResponseEntity.ok(notificationService.getStats());
    }

    @PostMapping("/delivery-webhook/infobip")
    @Operation(summary = "Webhook for Infobip delivery reports")
    public ResponseEntity<Void> handleInfobipDeliveryReport(@RequestBody InfobipDeliveryReport report) {
        log.info("Received Infobip delivery report");
        
        if (report.getResults() != null) {
            String messageId = report.getResults().getMessageId();
            String status = report.getResults().getStatus() != null ? 
                    report.getResults().getStatus().getName() : "UNKNOWN";
            String description = report.getResults().getStatus() != null ? 
                    report.getResults().getStatus().getDescription() : null;
            
            notificationService.updateDeliveryStatus(messageId, status, description);
        }
        
        return ResponseEntity.ok().build();
    }

    @GetMapping("/delivery-status/{messageId}")
    @Operation(summary = "Check delivery status of a message")
    public ResponseEntity<SmsService.DeliveryStatus> checkDeliveryStatus(@PathVariable String messageId) {
        return ResponseEntity.ok(smsService.checkDeliveryStatus(messageId));
    }

    @PostMapping("/test/sms")
    @Operation(summary = "Send a test SMS (for development)")
    public ResponseEntity<SmsService.SmsResult> sendTestSms(
            @RequestParam String phoneNumber,
            @RequestParam(defaultValue = "Test message from Table Banking") String message) {
        return ResponseEntity.ok(smsService.sendSms(phoneNumber, message));
    }

    @GetMapping("/health")
    @Operation(summary = "Health check endpoint")
    public ResponseEntity<Map<String, Object>> health() {
        long pendingCount = notificationLogRepository.countByStatus(NotificationStatus.PENDING);
        long failedCount = notificationLogRepository.countByStatus(NotificationStatus.FAILED);
        
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "pendingNotifications", pendingCount,
                "failedNotifications", failedCount
        ));
    }
}
