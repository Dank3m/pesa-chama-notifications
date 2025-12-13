package com.tablebanking.notification.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.math.BigDecimal;

/**
 * Infobip specific delivery report
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class InfobipDeliveryReport {
    private Results results;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Results {
        private String messageId;
        private String to;
        private String sentAt;
        private String doneAt;
        private Status status;
        private Price price;
        private Error error;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Status {
        private Integer groupId;
        private String groupName;
        private Integer id;
        private String name;
        private String description;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Price {
        private BigDecimal pricePerMessage;
        private String currency;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Error {
        private Integer groupId;
        private String groupName;
        private Integer id;
        private String name;
        private String description;
        private Boolean permanent;
    }
}
