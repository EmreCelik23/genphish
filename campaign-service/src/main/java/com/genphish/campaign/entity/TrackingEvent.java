package com.genphish.campaign.entity;

import com.genphish.campaign.entity.enums.TrackingEventType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tracking_events",
        indexes = {
                @Index(name = "idx_tracking_campaign", columnList = "campaign_id"),
                @Index(name = "idx_tracking_employee", columnList = "employee_id"),
                @Index(name = "idx_tracking_company", columnList = "company_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrackingEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "company_id", nullable = false)
    private UUID companyId; // Denormalized for fast multi-tenant queries

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private TrackingEventType eventType; // EMAIL_OPENED, LINK_CLICKED, CREDENTIALS_SUBMITTED

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @PrePersist
    protected void onCreate() {
        if (this.occurredAt == null) {
            this.occurredAt = LocalDateTime.now();
        }
    }
}
