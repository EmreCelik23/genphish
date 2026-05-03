package com.genphish.campaign.entity;

import com.genphish.campaign.entity.enums.DifficultyLevel;
import com.genphish.campaign.entity.enums.LanguageCode;
import com.genphish.campaign.entity.enums.TemplateCategory;
import com.genphish.campaign.entity.enums.TemplateStatus;
import com.genphish.campaign.entity.enums.TemplateType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "phishing_templates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PhishingTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "company_id")
    private UUID companyId; // Null for global static templates

    @Column(nullable = false, columnDefinition = "TEXT")
    private String name; // e.g., "Microsoft 365 Password Reset"

    @Column(nullable = false, columnDefinition = "TEXT")
    private String category; // e.g., "IT", "HR", "Finance", "AI Generated"

    @Enumerated(EnumType.STRING)
    @Column(name = "template_category", nullable = false)
    @Builder.Default
    private TemplateCategory templateCategory = TemplateCategory.CREDENTIAL_HARVESTING;

    @Enumerated(EnumType.STRING)
    @Column(name = "template_type", nullable = false)
    @Builder.Default
    private TemplateType type = TemplateType.STATIC;

    @Enumerated(EnumType.STRING)
    @Column(name = "template_status", nullable = false)
    @Builder.Default
    private TemplateStatus status = TemplateStatus.READY;

    // ── Content ──

    @Column(name = "email_subject", columnDefinition = "TEXT")
    private String emailSubject; // Nullable while generating

    @Column(name = "email_body", columnDefinition = "TEXT")
    private String emailBody; // Nullable while generating

    @Column(name = "landing_page_html", columnDefinition = "TEXT")
    private String landingPageHtml; // Nullable while generating

    // ── AI Generation Metadata ──

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty_level")
    private DifficultyLevel difficultyLevel; // Template difficulty

    @Enumerated(EnumType.STRING)
    @Column(name = "language_code")
    private LanguageCode languageCode; 

    @Column(name = "ai_prompt", columnDefinition = "TEXT")
    private String prompt; 

    @Column(name = "target_url", columnDefinition = "TEXT")
    private String targetUrl; 

    @Column(name = "reference_image_url", columnDefinition = "TEXT")
    private String referenceImageUrl;

    @Column(name = "mongo_template_id", columnDefinition = "TEXT")
    private String mongoTemplateId; 

    @Column(name = "fallback_content_used")
    @Builder.Default
    private boolean fallbackContentUsed = false;

    // ── Lifecycle ──

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "is_active")
    @Builder.Default
    private boolean isActive = true; // Soft-delete flag for templates

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
