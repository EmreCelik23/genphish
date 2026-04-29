package com.genphish.campaign.repository;

import com.genphish.campaign.entity.Campaign;
import com.genphish.campaign.entity.enums.CampaignStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface CampaignRepository extends JpaRepository<Campaign, UUID> {

    // Retrieves all active (non-deleted) campaigns for a company
    List<Campaign> findAllByCompanyIdAndIsDeleted(UUID companyId, boolean isDeleted);

    // Finds scheduled campaigns that are due (scheduler picks these up)
    List<Campaign> findAllByStatusAndScheduledForBeforeAndIsDeleted(CampaignStatus status, LocalDateTime dateTime, boolean isDeleted);

    // Count active campaigns per company (for dashboard)
    long countByCompanyIdAndIsDeleted(UUID companyId, boolean isDeleted);

    // Count campaigns by status per company
    long countByCompanyIdAndStatusAndIsDeleted(UUID companyId, CampaignStatus status, boolean isDeleted);
}
