package com.tablebanking.notification.repository;

import com.tablebanking.notification.entity.DeliveryStatusUpdate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeliveryStatusUpdateRepository extends JpaRepository<DeliveryStatusUpdate, UUID> {
    
    List<DeliveryStatusUpdate> findByNotificationIdOrderByReceivedAtDesc(UUID notificationId);
    
    Optional<DeliveryStatusUpdate> findFirstByProviderMessageIdOrderByReceivedAtDesc(String providerMessageId);
}
