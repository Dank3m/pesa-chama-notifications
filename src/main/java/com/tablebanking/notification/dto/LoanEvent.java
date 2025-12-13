package com.tablebanking.notification.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Loan event from main application
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class LoanEvent {
    private String eventId;
    private String eventType;  // LOAN_APPLIED, LOAN_APPROVED, LOAN_DISBURSED, LOAN_PAYMENT_RECEIVED, etc.
    private UUID loanId;
    private String loanNumber;
    private UUID memberId;
    private String memberName;
    private String phoneNumber;
    private String email;
    private UUID groupId;
    private String groupName;
    private BigDecimal amount;
    private BigDecimal outstandingBalance;
    private LocalDate dueDate;
    private LocalDate disbursementDate;
    private String status;
    private Instant timestamp;
}
