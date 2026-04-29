package com.genphish.campaign.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "employees", uniqueConstraints = @UniqueConstraint(columnNames = {"email", "company_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId; // Multi-tenant isolation key

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(nullable = false)
    private String email; // Target email address

    @Column(nullable = false)
    private String department; // Used for department-based targeting

    @Column(name = "risk_score")
    @Builder.Default
    private Double riskScore = 0.0; // Increases upon failing phishing tests

    @Column(name = "is_active")
    @Builder.Default
    private boolean isActive = true; // For soft-delete / deactivation

    @Column(name = "last_phished_at")
    private LocalDateTime lastPhishedAt; // Last simulation participation date

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}