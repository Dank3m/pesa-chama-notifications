package com.tablebanking.notification.scheduler;

import com.tablebanking.notification.entity.NotificationLog;
import com.tablebanking.notification.entity.ScheduledNotification;
import com.tablebanking.notification.entity.enums.NotificationStatus;
import com.tablebanking.notification.repository.NotificationLogRepository;
import com.tablebanking.notification.repository.ScheduledNotificationRepository;
import com.tablebanking.notification.service.EmailService;
import com.tablebanking.notification.service.SmsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationScheduler {

    private final NotificationLogRepository notificationLogRepository;
    private final ScheduledNotificationRepository scheduledNotificationRepository;
    private final SmsService smsService;
    private final EmailService emailService;

    @Value("${notification.retry.max-attempts:3}")
    private int maxRetryAttempts;

    /**
     * Retry failed notifications
     * Runs every 5 minutes
     */
    @Scheduled(fixedDelayString = "${notification.scheduler.retry-interval:300000}")
    @Transactional
    public void retryFailedNotifications() {
        log.debug("Running retry failed notifications job");
        
        List<NotificationLog> failedNotifications = notificationLogRepository
                .findByStatusAndRetryCountLessThan(NotificationStatus.FAILED, maxRetryAttempts);
        
        log.info("Found {} failed notifications to retry", failedNotifications.size());
        
        for (NotificationLog notification : failedNotifications) {
            try {
                retryNotification(notification);
            } catch (Exception e) {
                log.error("Error retrying notification {}: {}", notification.getId(), e.getMessage());
            }
        }
    }

    /**
     * Process scheduled notifications
     * Runs every minute
     */
    @Scheduled(fixedDelayString = "${notification.scheduler.scheduled-interval:60000}")
    @Transactional
    public void processScheduledNotifications() {
        log.debug("Processing scheduled notifications");
        
        List<ScheduledNotification> readyNotifications = scheduledNotificationRepository
                .findReadyToProcess(Instant.now());
        
        if (!readyNotifications.isEmpty()) {
            log.info("Found {} scheduled notifications ready to process", readyNotifications.size());
        }
        
        for (ScheduledNotification scheduled : readyNotifications) {
            try {
                NotificationLog notification = scheduled.getNotificationLog();
                if (notification != null) {
                    sendNotification(notification);
                }
                scheduledNotificationRepository.markAsProcessed(scheduled.getId(), Instant.now());
            } catch (Exception e) {
                log.error("Error processing scheduled notification {}: {}", scheduled.getId(), e.getMessage());
            }
        }
    }

    /**
     * Clean up old notifications
     * Runs daily at 2 AM
     */
    @Scheduled(cron = "${notification.scheduler.cleanup-cron:0 0 2 * * ?}")
    @Transactional
    public void cleanupOldNotifications() {
        log.info("Running notification cleanup job");
        
        // Find stale pending notifications (older than 24 hours)
        Instant cutoff = Instant.now().minus(24, ChronoUnit.HOURS);
        List<NotificationLog> staleNotifications = notificationLogRepository.findStaleNotifications(cutoff);
        
        log.info("Found {} stale notifications to mark as failed", staleNotifications.size());
        
        for (NotificationLog notification : staleNotifications) {
            notification.setStatus(NotificationStatus.FAILED);
            notification.setErrorMessage("Notification timed out");
            notificationLogRepository.save(notification);
        }
    }

    /**
     * Report notification statistics
     * Runs every hour
     */
    @Scheduled(cron = "${notification.scheduler.stats-cron:0 0 * * * ?}")
    public void reportStats() {
        long pending = notificationLogRepository.countByStatus(NotificationStatus.PENDING);
        long sent = notificationLogRepository.countByStatus(NotificationStatus.SENT);
        long delivered = notificationLogRepository.countByStatus(NotificationStatus.DELIVERED);
        long failed = notificationLogRepository.countByStatus(NotificationStatus.FAILED);
        
        log.info("Notification Stats - Pending: {}, Sent: {}, Delivered: {}, Failed: {}", 
                pending, sent, delivered, failed);
    }

    private void retryNotification(NotificationLog notification) {
        log.info("Retrying notification {} (attempt {})", notification.getId(), notification.getRetryCount() + 1);
        
        notification.setRetryCount(notification.getRetryCount() + 1);
        notification.setStatus(NotificationStatus.PENDING);
        notificationLogRepository.save(notification);
        
        sendNotification(notification);
    }

    private void sendNotification(NotificationLog notification) {
        try {
            switch (notification.getChannel()) {
                case SMS -> {
                    SmsService.SmsResult result = smsService.sendSms(
                            notification.getRecipient(), 
                            notification.getMessage()
                    );
                    
                    notification.setStatus(result.isSuccess() ? NotificationStatus.SENT : NotificationStatus.FAILED);
                    notification.setProviderMessageId(result.getMessageId());
                    notification.setProvider(result.getProvider());
                    
                    if (!result.isSuccess()) {
                        notification.setErrorMessage(result.getErrorMessage());
                    } else {
                        notification.setSentAt(Instant.now());
                    }
                }
                case EMAIL -> {
                    emailService.sendEmail(
                            notification.getRecipient(),
                            notification.getSubject(),
                            notification.getMessage()
                    );
                    
                    notification.setStatus(NotificationStatus.SENT);
                    notification.setSentAt(Instant.now());
                }
                default -> {
                    log.warn("Unknown notification channel: {}", notification.getChannel());
                    notification.setStatus(NotificationStatus.FAILED);
                    notification.setErrorMessage("Unknown channel");
                }
            }
        } catch (Exception e) {
            notification.setStatus(NotificationStatus.FAILED);
            notification.setErrorMessage(e.getMessage());
        }
        
        notificationLogRepository.save(notification);
    }
}
