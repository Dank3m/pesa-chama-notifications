package com.tablebanking.notification.dto;

import com.tablebanking.notification.entity.enums.NotificationChannel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Event published when a new member is created
 * Triggers notification service to send registration link via SMS/Email
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberRegistrationEvent {

    private String eventId;
    private String eventType;
    private LocalDateTime timestamp;

    private String memberId;
    private String memberNumber;
    private String firstName;
    private String lastName;
    private String fullName;
    private String email;
    private String phoneNumber;
    private String groupId;
    private String groupName;

    private String registrationToken;
    private String registrationLink;
    private LocalDateTime tokenExpiry;

    private String preferredChannel; // EMAIL, SMS, BOTH

    public static MemberRegistrationEvent create(
            String memberId,
            String memberNumber,
            String firstName,
            String lastName,
            String email,
            String phoneNumber,
            String groupId,
            String groupName,
            String registrationToken,
            String baseUrl,
            NotificationChannel channel
    ) {
        String registrationLink = String.format("%s/register?memberId=%s&token=%s",
                baseUrl, memberId, registrationToken);

        return MemberRegistrationEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("MEMBER_REGISTRATION")
                .timestamp(LocalDateTime.now())
                .memberId(memberId)
                .memberNumber(memberNumber)
                .firstName(firstName)
                .lastName(lastName)
                .fullName(firstName + " " + lastName)
                .email(email)
                .phoneNumber(phoneNumber)
                .groupId(groupId)
                .groupName(groupName)
                .registrationToken(registrationToken)
                .registrationLink(registrationLink)
                .tokenExpiry(LocalDateTime.now().plusDays(7))
                .preferredChannel(channel.name())
                .build();
    }
}