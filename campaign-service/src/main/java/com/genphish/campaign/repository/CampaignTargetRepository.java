package com.genphish.campaign.repository;

import com.genphish.campaign.entity.CampaignTarget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CampaignTargetRepository extends JpaRepository<CampaignTarget, UUID> {

    // Get all targeted employees for a campaign
    List<CampaignTarget> findAllByCampaignId(UUID campaignId);

    // Count targeted employees for a campaign
    long countByCampaignId(UUID campaignId);

    // Clean up targets when a campaign is deleted
    void deleteAllByCampaignId(UUID campaignId);
}
