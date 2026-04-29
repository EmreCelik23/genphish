package com.genphish.campaign.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "companies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id; // Universal unique identifier

    @Column(nullable = false, unique = true)
    private String name; // Company name

    @Column(nullable = false, unique = true)
    private String domain; // e.g., acme.com (For email validation)

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt; // Audit field

    @Column(name = "is_active")
    @Builder.Default
    private boolean isActive = true; // Soft-delete flag for companies

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}