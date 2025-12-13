package com.tablebanking.notification.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tablebanking.notification.dto.ContributionEvent;
import com.tablebanking.notification.dto.LoanEvent;
import com.tablebanking.notification.dto.NotificationEvent;
import com.tablebanking.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventConsumer {

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    /**
     * Consume notification events from the notification-events topic
     */
    @KafkaListener(
            topics = "${kafka.topics.notification-events:notification-events}",
            groupId = "${spring.kafka.consumer.group-id:notification-service-group}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeNotificationEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        log.info("Received notification event: key={}, partition={}, offset={}", 
                record.key(), record.partition(), record.offset());
        
        try {
            NotificationEvent event = objectMapper.readValue(record.value(), NotificationEvent.class);
            notificationService.processNotificationEvent(event);
            ack.acknowledge();
            log.info("Successfully processed notification event: {}", event.getEventId());
            
        } catch (JsonProcessingException e) {
            log.error("Failed to parse notification event: {}", e.getMessage());
            // Acknowledge to avoid reprocessing bad messages
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error processing notification event: {}", e.getMessage(), e);
            // Don't acknowledge - will be reprocessed
        }
    }

    /**
     * Consume contribution events from the contribution-events topic
     */
    @KafkaListener(
            topics = "${kafka.topics.contribution-events:contribution-events}",
            groupId = "${spring.kafka.consumer.group-id:notification-service-group}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeContributionEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        log.info("Received contribution event: key={}, partition={}, offset={}", 
                record.key(), record.partition(), record.offset());
        
        try {
            ContributionEvent event = objectMapper.readValue(record.value(), ContributionEvent.class);
            
            // Only process relevant events that need notifications
            if (shouldNotifyForContributionEvent(event.getEventType())) {
                notificationService.processContributionEvent(event);
            }
            
            ack.acknowledge();
            log.info("Successfully processed contribution event: {} - {}", event.getEventId(), event.getEventType());
            
        } catch (JsonProcessingException e) {
            log.error("Failed to parse contribution event: {}", e.getMessage());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error processing contribution event: {}", e.getMessage(), e);
        }
    }

    /**
     * Consume loan events from the loan-events topic
     */
    @KafkaListener(
            topics = "${kafka.topics.loan-events:loan-events}",
            groupId = "${spring.kafka.consumer.group-id:notification-service-group}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeLoanEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        log.info("Received loan event: key={}, partition={}, offset={}", 
                record.key(), record.partition(), record.offset());
        
        try {
            LoanEvent event = objectMapper.readValue(record.value(), LoanEvent.class);
            
            // Only process relevant events that need notifications
            if (shouldNotifyForLoanEvent(event.getEventType())) {
                notificationService.processLoanEvent(event);
            }
            
            ack.acknowledge();
            log.info("Successfully processed loan event: {} - {}", event.getEventId(), event.getEventType());
            
        } catch (JsonProcessingException e) {
            log.error("Failed to parse loan event: {}", e.getMessage());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error processing loan event: {}", e.getMessage(), e);
        }
    }

    private boolean shouldNotifyForContributionEvent(String eventType) {
        return switch (eventType) {
            case "CONTRIBUTION_RECEIVED",
                 "CONTRIBUTION_REMINDER",
                 "CONTRIBUTION_DEFAULTED",
                 "CONTRIBUTION_OVERDUE" -> true;
            default -> false;
        };
    }

    private boolean shouldNotifyForLoanEvent(String eventType) {
        return switch (eventType) {
            case "LOAN_APPROVED",
                 "LOAN_DISBURSED",
                 "LOAN_PAYMENT_RECEIVED",
                 "LOAN_PAYMENT_REMINDER",
                 "LOAN_OVERDUE",
                 "LOAN_CREATED_FROM_DEFAULT",
                 "LOAN_PAID_OFF" -> true;
            default -> false;
        };
    }
}
