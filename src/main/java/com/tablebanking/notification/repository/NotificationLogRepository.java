package com.tablebanking.notification.repository;

import com.tablebanking.notification.entity.NotificationLog;
import com.tablebanking.notification.entity.enums.NotificationChannel;
import com.tablebanking.notification.entity.enums.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {
    
    Optional<NotificationLog> findByEventId(String eventId);
    
    List<NotificationLog> findByMemberId(UUID memberId);
    
    Page<NotificationLog> findByMemberIdOrderByCreatedAtDesc(UUID memberId, Pageable pageable);
    
    List<NotificationLog> findByStatus(NotificationStatus status);
    
    List<NotificationLog> findByStatusAndRetryCountLessThan(NotificationStatus status, int maxRetries);
    
    @Query("SELECT n FROM NotificationLog n WHERE n.status = :status AND n.scheduledAt <= :now")
    List<NotificationLog> findScheduledNotificationsReady(
            @Param("status") NotificationStatus status, 
            @Param("now") Instant now);
    
    @Query("SELECT n FROM NotificationLog n WHERE n.status = 'PENDING' AND n.createdAt < :cutoff")
    List<NotificationLog> findStaleNotifications(@Param("cutoff") Instant cutoff);
    
    @Query("SELECT COUNT(n) FROM NotificationLog n WHERE n.status = :status")
    long countByStatus(@Param("status") NotificationStatus status);
    
    @Query("SELECT COUNT(n) FROM NotificationLog n WHERE n.channel = :channel AND n.status = :status")
    long countByChannelAndStatus(
            @Param("channel") NotificationChannel channel, 
            @Param("status") NotificationStatus status);
    
    @Query("SELECT COUNT(n) FROM NotificationLog n WHERE n.eventType = :eventType AND n.createdAt >= :since")
    long countByEventTypeSince(
            @Param("eventType") String eventType, 
            @Param("since") Instant since);
    
    boolean existsByEventId(String eventId);
    
    @Query("SELECT n FROM NotificationLog n WHERE n.providerMessageId = :providerMessageId")
    Optional<NotificationLog> findByProviderMessageId(@Param("providerMessageId") String providerMessageId);
    
    @Modifying
    @Query("UPDATE NotificationLog n SET n.status = :status, n.updatedAt = :now WHERE n.id = :id")
    void updateStatus(@Param("id") UUID id, @Param("status") NotificationStatus status, @Param("now") Instant now);
}
