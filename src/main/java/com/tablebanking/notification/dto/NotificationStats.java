package com.tablebanking.notification.dto;

import lombok.*;

import java.util.Map;

/**
 * Notification statistics
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationStats {
    private long totalSent;
    private long totalDelivered;
    private long totalFailed;
    private long totalPending;
    private double deliveryRate;
    private Map<String, Long> byChannel;
    private Map<String, Long> byEventType;
}
