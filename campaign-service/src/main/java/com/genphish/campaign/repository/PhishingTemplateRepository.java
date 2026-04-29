package com.genphish.campaign.repository;

import com.genphish.campaign.entity.PhishingTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PhishingTemplateRepository extends JpaRepository<PhishingTemplate, UUID> {

    // Filter active templates by category
    List<PhishingTemplate> findAllByCategoryAndIsActive(String category, boolean isActive);

    // Get all active templates
    List<PhishingTemplate> findAllByIsActive(boolean isActive);

    // Get an active template by ID
    Optional<PhishingTemplate> findByIdAndIsActive(UUID id, boolean isActive);
}
