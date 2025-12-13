package com.tablebanking.notification.dto;

import lombok.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Request to send a notification manually
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SendNotificationRequest {
    private UUID memberId;
    private String memberName;
    private String phoneNumber;
    private String email;
    private UUID groupId;
    private String groupName;
    private String templateCode;
    private String channel;  // SMS, EMAIL, PUSH
    private Map<String, Object> variables;
    private Instant scheduledAt;  // Optional - for scheduled notifications
}
