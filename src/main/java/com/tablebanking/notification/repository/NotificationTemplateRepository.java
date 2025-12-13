package com.tablebanking.notification.repository;

import com.tablebanking.notification.entity.NotificationTemplate;
import com.tablebanking.notification.entity.enums.NotificationChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, UUID> {
    
    Optional<NotificationTemplate> findByTemplateCode(String templateCode);
    
    List<NotificationTemplate> findByChannelAndIsActiveTrue(NotificationChannel channel);
    
    List<NotificationTemplate> findByIsActiveTrue();
    
    boolean existsByTemplateCode(String templateCode);
}
