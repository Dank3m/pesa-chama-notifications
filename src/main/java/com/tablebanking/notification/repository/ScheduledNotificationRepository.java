package com.tablebanking.notification.repository;

import com.tablebanking.notification.entity.ScheduledNotification;
import com.tablebanking.notification.entity.enums.ScheduleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface ScheduledNotificationRepository extends JpaRepository<ScheduledNotification, UUID> {
    
    @Query("SELECT s FROM ScheduledNotification s WHERE s.status = 'PENDING' AND s.scheduledTime <= :now")
    List<ScheduledNotification> findReadyToProcess(@Param("now") Instant now);
    
    List<ScheduledNotification> findByStatus(ScheduleStatus status);
    
    @Modifying
    @Query("UPDATE ScheduledNotification s SET s.status = 'PROCESSED', s.processedAt = :now WHERE s.id = :id")
    void markAsProcessed(@Param("id") UUID id, @Param("now") Instant now);
}
