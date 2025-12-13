package com.tablebanking.notification.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Contribution event from main application
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ContributionEvent {
    private String eventId;
    private String eventType;  // CONTRIBUTION_RECEIVED, CONTRIBUTION_REMINDER, CONTRIBUTION_DEFAULTED
    private UUID contributionId;
    private UUID memberId;
    private String memberName;
    private String phoneNumber;
    private String email;
    private UUID groupId;
    private String groupName;
    private LocalDate cycleMonth;
    private BigDecimal expectedAmount;
    private BigDecimal paidAmount;
    private LocalDate dueDate;
    private String status;
    private Instant timestamp;
}
