package com.tablebanking.notification.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.time.Instant;
import java.util.Map;

/**
 * Delivery status update from SMS provider webhook
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeliveryStatusCallback {
    private String messageId;
    private String to;
    private String status;
    private String description;
    private Instant timestamp;
    private Map<String, Object> rawPayload;
}
