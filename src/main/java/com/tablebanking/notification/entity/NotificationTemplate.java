package com.tablebanking.notification.entity;

import com.tablebanking.notification.entity.enums.NotificationChannel;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

@Entity
@Table(name = "notification_templates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationTemplate extends BaseEntity {
    
    @Column(name = "template_code", nullable = false, unique = true, length = 50)
    private String templateCode;
    
    @Column(name = "template_name", nullable = false, length = 100)
    private String templateName;
    
    @Column(name = "description")
    private String description;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private NotificationChannel channel;
    
    @Column(name = "subject_template")
    private String subjectTemplate;
    
    @Column(name = "body_template", nullable = false, columnDefinition = "TEXT")
    private String bodyTemplate;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "variables", columnDefinition = "jsonb")
    private List<String> variables;
    
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
