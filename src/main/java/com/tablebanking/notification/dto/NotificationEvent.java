package com.tablebanking.notification.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Generic notification event received from Kafka
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class NotificationEvent {
    private String eventId;
    private String eventType;
    private UUID memberId;
    private String memberName;
    private String phoneNumber;
    private String email;
    private UUID groupId;
    private String groupName;
    private Map<String, Object> data;
    private Instant timestamp;
}
