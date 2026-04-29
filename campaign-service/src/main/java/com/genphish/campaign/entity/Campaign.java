package com.genphish.campaign.entity;

import com.genphish.campaign.entity.enums.CampaignStatus;
import com.genphish.campaign.entity.enums.DifficultyLevel;
import com.genphish.campaign.entity.enums.TargetingType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "campaigns")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Campaign {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId; // Tenant reference (multi-tenancy)

    @Column(nullable = false)
    private String name; // Campaign title

    // ── Targeting ──

    @Enumerated(EnumType.STRING)
    @Column(name = "targeting_type", nullable = false)
    private TargetingType targetingType; // ALL_COMPANY, DEPARTMENT, INDIVIDUAL, HIGH_RISK

    @Column(name = "target_department")
    private String targetDepartment; // Used when targetingType = DEPARTMENT

    // ── AI vs Static ──

    @Column(name = "is_ai_generated", nullable = false)
    private boolean isAiGenerated; // true = AI mode, false = static template

    @Column(name = "ai_prompt", columnDefinition = "TEXT")
    private String aiPrompt; // AI mode: user's scenario prompt

    @Column(name = "target_url")
    private String targetUrl; // AI mode: URL to clone for landing page

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty_level")
    private DifficultyLevel difficultyLevel; // AMATEUR or PROFESSIONAL

    @Column(name = "static_template_id")
    private UUID staticTemplateId; // Static mode: reference to phishing_templates table

    @Column(name = "mongo_template_id")
    private String mongoTemplateId; // App-level join for AI-generated content in MongoDB

    @Column(name = "target_count")
    @Builder.Default
    private long targetCount = 0; // Number of employees targeted (calculated at launch)

    // ── Lifecycle ──

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CampaignStatus status; // Current lifecycle state

    @Column(name = "scheduled_for")
    private LocalDateTime scheduledFor; // Future execution time (null = immediate)

    @Column(name = "is_deleted")
    @Builder.Default
    private boolean isDeleted = false; // Soft-delete flag (preserves tracking data)

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt; // When the campaign was soft-deleted

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}