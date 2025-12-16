package com.tablebanking.notification.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class SmsService {

    private final WebClient webClient;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${notification.sms.enabled:false}")
    private boolean smsEnabled;

    @Value("${notification.sms.infobip.api-key:}")
    private String apiKey;

    @Value("${notification.sms.infobip.sender-id:TableBank}")
    private String senderId;

    @Value("${notification.sms.rate-limit.max-per-minute:60}")
    private int maxPerMinute;

    @Value("${notification.sms.rate-limit.max-per-hour:500}")
    private int maxPerHour;

    public SmsService(
            @Qualifier("infobipWebClient") WebClient webClient,
            RedisTemplate<String, String> redisTemplate) {
        this.webClient = webClient;
        this.redisTemplate = redisTemplate;
    }

    /**
     * Send SMS via Infobip
     */
    @Retryable(
            retryFor = {WebClientResponseException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public SmsResult sendSms(String phoneNumber, String message) {
        if (!smsEnabled) {
            log.info("SMS disabled. Would send to {}: {}", phoneNumber, message);
            return SmsResult.builder()
                    .success(true)
                    .messageId("DISABLED-" + System.currentTimeMillis())
                    .provider("disabled")
                    .build();
        }

        String normalizedPhone = normalizePhoneNumber(phoneNumber);

        if (!checkRateLimit(normalizedPhone)) {
            log.warn("Rate limit exceeded for phone: {}", normalizedPhone);
            return SmsResult.builder()
                    .success(false)
                    .errorMessage("Rate limit exceeded")
                    .provider("infobip")
                    .build();
        }

        try {
            InfobipSmsRequest request = InfobipSmsRequest.builder()
                    .messages(List.of(
                            InfobipSmsRequest.Message.builder()
                                    .from(senderId)
                                    .destinations(List.of(
                                            InfobipSmsRequest.Destination.builder()
                                                    .to(normalizedPhone)
                                                    .build()
                                    ))
                                    .text(message)
                                    .build()
                    ))
                    .build();

            InfobipSmsResponse response = webClient.post()
                    .uri("/sms/2/text/advanced")
                    .header("Authorization", "App " + apiKey)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(InfobipSmsResponse.class)
                    .block(Duration.ofSeconds(30));

            if (response != null && response.getMessages() != null && !response.getMessages().isEmpty()) {
                InfobipSmsResponse.MessageResponse msgResponse = response.getMessages().get(0);

                incrementRateLimit(normalizedPhone);

                log.info("SMS sent successfully to {}. MessageId: {}", normalizedPhone, msgResponse.getMessageId());

                return SmsResult.builder()
                        .success(true)
                        .messageId(msgResponse.getMessageId())
                        .provider("infobip")
                        .providerStatus(msgResponse.getStatus() != null ? msgResponse.getStatus().getName() : null)
                        .build();
            }

            return SmsResult.builder()
                    .success(false)
                    .errorMessage("Empty response from Infobip")
                    .provider("infobip")
                    .build();

        } catch (WebClientResponseException e) {
            log.error("Infobip API error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            return SmsResult.builder()
                    .success(false)
                    .errorMessage("API error: " + e.getStatusCode())
                    .provider("infobip")
                    .build();
        } catch (Exception e) {
            log.error("Failed to send SMS to {}: {}", normalizedPhone, e.getMessage());
            return SmsResult.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .provider("infobip")
                    .build();
        }
    }

    /**
     * Send bulk SMS
     */
    public List<SmsResult> sendBulkSms(List<BulkSmsRequest> requests) {
        return requests.stream()
                .map(req -> sendSms(req.getPhoneNumber(), req.getMessage()))
                .toList();
    }

    /**
     * Check delivery status
     */
    @SuppressWarnings("unchecked")
    public DeliveryStatus checkDeliveryStatus(String messageId) {
        if (!smsEnabled) {
            return DeliveryStatus.builder()
                    .messageId(messageId)
                    .status("UNKNOWN")
                    .build();
        }

        try {
            Map<String, Object> response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/sms/1/reports")
                            .queryParam("messageId", messageId)
                            .build())

                    .header("Authorization", "App " + apiKey)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(10));

            if (response != null && response.containsKey("results")) {
                List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");
                if (!results.isEmpty()) {
                    Map<String, Object> result = results.get(0);
                    Map<String, Object> status = (Map<String, Object>) result.get("status");

                    return DeliveryStatus.builder()
                            .messageId(messageId)
                            .status(status != null ? (String) status.get("name") : "UNKNOWN")
                            .description(status != null ? (String) status.get("description") : null)
                            .build();
                }
            }

            return DeliveryStatus.builder()
                    .messageId(messageId)
                    .status("UNKNOWN")
                    .build();

        } catch (Exception e) {
            log.error("Failed to check delivery status for {}: {}", messageId, e.getMessage());
            return DeliveryStatus.builder()
                    .messageId(messageId)
                    .status("ERROR")
                    .description(e.getMessage())
                    .build();
        }
    }

    private String normalizePhoneNumber(String phoneNumber) {
        String cleaned = phoneNumber.replaceAll("[^0-9+]", "");

        if (cleaned.startsWith("0")) {
            cleaned = "254" + cleaned.substring(1);
        }

        if (!cleaned.startsWith("+")) {
            cleaned = "+" + cleaned;
        }

        return cleaned;
    }

    private boolean checkRateLimit(String phoneNumber) {
        String minuteKey = "sms:rate:" + phoneNumber + ":minute";
        String hourKey = "sms:rate:" + phoneNumber + ":hour";

        String minuteCount = redisTemplate.opsForValue().get(minuteKey);
        String hourCount = redisTemplate.opsForValue().get(hourKey);

        int currentMinute = minuteCount != null ? Integer.parseInt(minuteCount) : 0;
        int currentHour = hourCount != null ? Integer.parseInt(hourCount) : 0;

        return currentMinute < maxPerMinute && currentHour < maxPerHour;
    }

    private void incrementRateLimit(String phoneNumber) {
        String minuteKey = "sms:rate:" + phoneNumber + ":minute";
        String hourKey = "sms:rate:" + phoneNumber + ":hour";

        redisTemplate.opsForValue().increment(minuteKey);
        redisTemplate.expire(minuteKey, Duration.ofMinutes(1));

        redisTemplate.opsForValue().increment(hourKey);
        redisTemplate.expire(hourKey, Duration.ofHours(1));
    }

    // DTOs
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SmsResult {
        private boolean success;
        private String messageId;
        private String provider;
        private String providerStatus;
        private String errorMessage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BulkSmsRequest {
        private String phoneNumber;
        private String message;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeliveryStatus {
        private String messageId;
        private String status;
        private String description;
        private Instant deliveredAt;
    }

    // Infobip Request/Response DTOs
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    private static class InfobipSmsRequest {
        private List<Message> messages;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Message {
            private String from;
            private List<Destination> destinations;
            private String text;
        }

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Destination {
            private String to;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class InfobipSmsResponse {
        private String bulkId;
        private List<MessageResponse> messages;

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class MessageResponse {
            private String to;
            private MessageStatus status;
            private String messageId;
        }

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class MessageStatus {
            private Integer groupId;
            private String groupName;
            private Integer id;
            private String name;
            private String description;
        }
    }
}