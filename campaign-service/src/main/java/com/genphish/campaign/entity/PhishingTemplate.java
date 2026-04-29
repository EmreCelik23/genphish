package com.genphish.campaign.entity;

import com.genphish.campaign.entity.enums.DifficultyLevel;
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

    @Column(nullable = false)
    private String name; // e.g., "Microsoft 365 Password Reset"

    @Column(nullable = false)
    private String category; // e.g., "IT", "HR", "Finance"

    @Column(name = "email_subject", nullable = false)
    private String emailSubject; // Static email subject line

    @Column(name = "email_body", nullable = false, columnDefinition = "TEXT")
    private String emailBody; // Static email HTML body (supports {{name}}, {{department}} placeholders)

    @Column(name = "landing_page_html", columnDefinition = "TEXT")
    private String landingPageHtml; // Static fake login page HTML

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty_level")
    private DifficultyLevel difficultyLevel; // Template difficulty

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
