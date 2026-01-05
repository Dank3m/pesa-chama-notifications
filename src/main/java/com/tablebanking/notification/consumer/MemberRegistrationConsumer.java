package com.tablebanking.notification.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tablebanking.notification.dto.MemberRegistrationEvent;
import com.tablebanking.notification.service.EmailService;
import com.tablebanking.notification.service.SmsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Kafka Consumer for Member Registration Events
 * Sends registration link via SMS and/or Email
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MemberRegistrationConsumer {

    private final EmailService emailService;
    private final SmsService smsService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${kafka.topics.member-registration:member-registration-events}",
            groupId = "${spring.kafka.consumer.group-id:pesa-chama-group}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeMemberRegistrationEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        log.info("Received member registration event: key={}, partition={}, offset={}",
                record.key(), record.partition(), record.offset());

        try {
            MemberRegistrationEvent event = objectMapper.readValue(record.value(), MemberRegistrationEvent.class);
            processRegistrationEvent(event);
            ack.acknowledge();
            log.info("Successfully processed member registration event: {}", event.getEventId());

        } catch (JsonProcessingException e) {
            log.error("Failed to parse member registration event: {}", e.getMessage());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error processing member registration event: {}", e.getMessage(), e);
        }
    }

    private void processRegistrationEvent(MemberRegistrationEvent event) {
        String channel = event.getPreferredChannel();

        switch (channel) {
            case "EMAIL":
                sendEmailNotification(event);
                break;
            case "SMS":
                sendSmsNotification(event);
                break;
            case "BOTH":
                sendEmailNotification(event);
                sendSmsNotification(event);
                break;
            default:
                log.warn("Unknown notification channel: {}, defaulting to EMAIL", channel);
                sendEmailNotification(event);
                sendSmsNotification(event);
        }
    }

    private void sendEmailNotification(MemberRegistrationEvent event) {
        if (event.getEmail() == null || event.getEmail().isBlank()) {
            log.warn("No email address for member: {}, skipping email notification", event.getMemberId());
            return;
        }

        try {
            String subject = String.format("Welcome to %s - Complete Your Registration", event.getGroupName());

            Map<String, Object> variables = Map.of(
                    "memberName", event.getFullName(),
                    "groupName", event.getGroupName(),
                    "memberNumber", event.getMemberNumber(),
                    "registrationLink", event.getRegistrationLink()
            );

            emailService.sendHtmlEmail(event.getEmail(), subject, "member-registration", variables);
            log.info("Registration email sent to: {}", event.getEmail());
        } catch (Exception e) {
            log.error("Failed to send registration email to: {}", event.getEmail(), e);
        }
    }

    private void sendSmsNotification(MemberRegistrationEvent event) {
        if (event.getPhoneNumber() == null || event.getPhoneNumber().isBlank()) {
            log.warn("No phone number for member: {}, skipping SMS notification", event.getMemberId());
            return;
        }

        try {
            String message = String.format(
                    "Welcome to %s! Complete your registration here: %s (Expires in 7 days)",
                    event.getGroupName(),
                    event.getRegistrationLink()
            );
            smsService.sendSms(event.getPhoneNumber(), message);
            log.info("Registration SMS sent to: {}", event.getPhoneNumber());
        } catch (Exception e) {
            log.error("Failed to send registration SMS to: {}", event.getPhoneNumber(), e);
        }
    }
}