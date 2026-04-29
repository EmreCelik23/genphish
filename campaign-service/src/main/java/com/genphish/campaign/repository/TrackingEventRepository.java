package com.genphish.campaign.repository;

import com.genphish.campaign.entity.TrackingEvent;
import com.genphish.campaign.entity.enums.TrackingEventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TrackingEventRepository extends JpaRepository<TrackingEvent, UUID> {

    // All events for a specific campaign (campaign-level analytics)
    List<TrackingEvent> findAllByCampaignId(UUID campaignId);

    // All events for a specific employee (user risk profile)
    List<TrackingEvent> findAllByEmployeeId(UUID employeeId);

    // All events for a specific company (department-level analytics)
    List<TrackingEvent> findAllByCompanyId(UUID companyId);

    // Count events by type for a campaign (e.g., how many link_clicked in campaign X)
    long countByCampaignIdAndEventType(UUID campaignId, TrackingEventType eventType);

    // Count events by type for an employee (e.g., how many times did Ahmet submit credentials)
    long countByEmployeeIdAndEventType(UUID employeeId, TrackingEventType eventType);

    // Idempotency check: prevent duplicate tracking events
    boolean existsByCampaignIdAndEmployeeIdAndEventType(UUID campaignId, UUID employeeId, TrackingEventType eventType);
}
