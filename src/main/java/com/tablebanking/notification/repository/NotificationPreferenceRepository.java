package com.tablebanking.notification.repository;

import com.tablebanking.notification.entity.NotificationPreference;
import com.tablebanking.notification.entity.enums.NotificationChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, UUID> {
    
    List<NotificationPreference> findByMemberId(UUID memberId);
    
    Optional<NotificationPreference> findByMemberIdAndChannelAndEventType(
            UUID memberId, NotificationChannel channel, String eventType);
    
    @Query("SELECT p FROM NotificationPreference p WHERE p.memberId = :memberId AND p.isEnabled = true")
    List<NotificationPreference> findEnabledPreferences(@Param("memberId") UUID memberId);
    
    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN false ELSE true END FROM NotificationPreference p " +
           "WHERE p.memberId = :memberId AND p.channel = :channel AND p.eventType = :eventType AND p.isEnabled = false")
    boolean isNotificationEnabled(
            @Param("memberId") UUID memberId, 
            @Param("channel") NotificationChannel channel, 
            @Param("eventType") String eventType);
}
