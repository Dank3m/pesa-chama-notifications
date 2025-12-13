package com.tablebanking.notification.dto;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Response after sending notification
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationResponse {
    private UUID notificationId;
    private String status;
    private String message;
    private String providerMessageId;
    private Instant sentAt;
}
