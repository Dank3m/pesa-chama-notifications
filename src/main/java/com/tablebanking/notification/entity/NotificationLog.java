package com.tablebanking.notification.entity;

import com.tablebanking.notification.entity.enums.NotificationChannel;
import com.tablebanking.notification.entity.enums.NotificationStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "notification_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationLog extends BaseEntity {
    
    @Column(name = "event_id", length = 100)
    private String eventId;
    
    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;
    
    @Column(name = "member_id", nullable = false)
    private UUID memberId;
    
    @Column(name = "member_name", length = 100)
    private String memberName;
    
    @Column(name = "group_id")
    private UUID groupId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private NotificationChannel channel;
    
    @Column(name = "recipient", nullable = false)
    private String recipient;
    
    @Column(name = "template_code", length = 50)
    private String templateCode;
    
    @Column(name = "subject")
    private String subject;
    
    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "variables", columnDefinition = "jsonb")
    private Map<String, Object> variables;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private NotificationStatus status = NotificationStatus.PENDING;
    
    @Column(name = "provider", length = 50)
    private String provider;
    
    @Column(name = "provider_message_id")
    private String providerMessageId;
    
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    
    @Column(name = "retry_count")
    @Builder.Default
    private Integer retryCount = 0;
    
    @Column(name = "scheduled_at")
    private Instant scheduledAt;
    
    @Column(name = "sent_at")
    private Instant sentAt;
    
    @Column(name = "delivered_at")
    private Instant deliveredAt;
    
    @Column(name = "read_at")
    private Instant readAt;
}
