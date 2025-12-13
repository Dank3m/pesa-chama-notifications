package com.tablebanking.notification.entity;

import com.tablebanking.notification.entity.enums.ScheduleStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "scheduled_notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduledNotification {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notification_log_id")
    private NotificationLog notificationLog;
    
    @Column(name = "scheduled_time", nullable = false)
    private Instant scheduledTime;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ScheduleStatus status = ScheduleStatus.PENDING;
    
    @Column(name = "processed_at")
    private Instant processedAt;
    
    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
