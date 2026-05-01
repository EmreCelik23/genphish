package com.genphish.campaign.service.impl;

import com.genphish.campaign.dto.response.CampaignFunnelResponse;
import com.genphish.campaign.dto.response.DashboardResponse;
import com.genphish.campaign.dto.response.TrackingEventResponse;
import com.genphish.campaign.entity.Campaign;
import com.genphish.campaign.entity.Employee;
import com.genphish.campaign.entity.TrackingEvent;
import com.genphish.campaign.entity.enums.CampaignStatus;
import com.genphish.campaign.entity.enums.TrackingEventType;
import com.genphish.campaign.repository.CampaignRepository;
import com.genphish.campaign.repository.EmployeeRepository;
import com.genphish.campaign.repository.TrackingEventRepository;
import com.genphish.campaign.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsServiceImpl implements AnalyticsService {

    private final CampaignRepository campaignRepository;
    private final EmployeeRepository employeeRepository;
    private final TrackingEventRepository trackingEventRepository;

    @Override
    public DashboardResponse getDashboard(UUID companyId) {

        // ── Company-wide stats ──
        long totalEmployees = employeeRepository.countByCompanyIdAndIsActive(companyId, true);
        long totalCampaigns = campaignRepository.countByCompanyIdAndIsDeleted(companyId, false);
        long activeCampaigns = campaignRepository.countByCompanyIdAndStatusAndIsDeleted(companyId, CampaignStatus.IN_PROGRESS, false);

        // Only consider active employees for accurate rates
        List<Employee> employees = employeeRepository.findAllByCompanyIdAndIsActive(companyId, true);
        Set<UUID> activeEmployeeIds = employees.stream()
                .map(Employee::getId)
                .collect(Collectors.toSet());

        List<TrackingEvent> activeEvents = trackingEventRepository.findAllByCompanyId(companyId).stream()
                .filter(e -> activeEmployeeIds.contains(e.getEmployeeId()))
                .toList();

        // Overall phishing rate: % of active employees who submitted credentials at least once
        long employeesPhished = activeEvents.stream()
                .filter(e -> isRiskActionEvent(e.getEventType()))
                .map(TrackingEvent::getEmployeeId)
                .distinct()
                .count();
        double overallPhishingRate = totalEmployees > 0 ? (double) employeesPhished / totalEmployees * 100 : 0;

        // ── Department-level stats ──
        Map<String, List<Employee>> byDepartment = employees.stream()
                .collect(Collectors.groupingBy(Employee::getDepartment));

        List<DashboardResponse.DepartmentStats> departmentStats = byDepartment.entrySet().stream()
                .map(entry -> {
                    String dept = entry.getKey();
                    Set<UUID> deptEmployeeIds = entry.getValue().stream()
                            .map(Employee::getId)
                            .collect(Collectors.toSet());

                    List<TrackingEvent> deptEvents = activeEvents.stream()
                            .filter(e -> deptEmployeeIds.contains(e.getEmployeeId()))
                            .toList();

                    long opened = deptEvents.stream()
                            .filter(e -> e.getEventType() == TrackingEventType.EMAIL_OPENED).count();
                    long clicked = deptEvents.stream()
                            .filter(e -> e.getEventType() == TrackingEventType.LINK_CLICKED).count();
                    long submitted = deptEvents.stream()
                            .filter(e -> e.getEventType() == TrackingEventType.CREDENTIALS_SUBMITTED).count();
                    long downloadTriggered = deptEvents.stream()
                            .filter(e -> e.getEventType() == TrackingEventType.DOWNLOAD_TRIGGERED).count();
                    long consentGranted = deptEvents.stream()
                            .filter(e -> e.getEventType() == TrackingEventType.CONSENT_GRANTED).count();
                    long actionsTaken = deptEvents.stream()
                            .filter(e -> isRiskActionEvent(e.getEventType())).count();

                    long deptPhished = deptEvents.stream()
                            .filter(e -> isRiskActionEvent(e.getEventType()))
                            .map(TrackingEvent::getEmployeeId)
                            .distinct()
                            .count();

                    double phishingRate = !deptEmployeeIds.isEmpty()
                            ? (double) deptPhished / deptEmployeeIds.size() * 100 : 0;

                    return DashboardResponse.DepartmentStats.builder()
                            .department(dept)
                            .employeeCount(deptEmployeeIds.size())
                            .phishingRate(phishingRate)
                            .emailsOpened(opened)
                            .linksClicked(clicked)
                            .credentialsSubmitted(submitted)
                            .downloadTriggered(downloadTriggered)
                            .consentGranted(consentGranted)
                            .actionsTaken(actionsTaken)
                            .build();
                })
                .toList();

        // ── Campaign-level stats ──
        List<Campaign> campaigns = campaignRepository.findAllByCompanyIdAndIsDeleted(companyId, false);
        List<DashboardResponse.CampaignStats> recentCampaigns = campaigns.stream()
                .sorted(Comparator.comparing(Campaign::getCreatedAt).reversed())
                .limit(10)
                .map(campaign -> {
                    List<TrackingEvent> campaignEvents = activeEvents.stream()
                            .filter(e -> e.getCampaignId().equals(campaign.getId()))
                            .toList();

                    long opened = campaignEvents.stream()
                            .filter(e -> e.getEventType() == TrackingEventType.EMAIL_OPENED).count();
                    long clicked = campaignEvents.stream()
                            .filter(e -> e.getEventType() == TrackingEventType.LINK_CLICKED).count();
                    long submitted = campaignEvents.stream()
                            .filter(e -> e.getEventType() == TrackingEventType.CREDENTIALS_SUBMITTED).count();
                    long downloadTriggered = campaignEvents.stream()
                            .filter(e -> e.getEventType() == TrackingEventType.DOWNLOAD_TRIGGERED).count();
                    long consentGranted = campaignEvents.stream()
                            .filter(e -> e.getEventType() == TrackingEventType.CONSENT_GRANTED).count();
                    long actionsTaken = campaignEvents.stream()
                            .filter(e -> isRiskActionEvent(e.getEventType())).count();

                    // Real target count from campaign launch
                    long targetCount = campaign.getTargetCount();

                    long safeEmployees = targetCount - campaignEvents.stream()
                            .filter(e -> isRiskActionEvent(e.getEventType()))
                            .map(TrackingEvent::getEmployeeId)
                            .distinct()
                            .count();

                    double successRate = targetCount > 0 ? (double) safeEmployees / targetCount * 100 : 100;

                    return DashboardResponse.CampaignStats.builder()
                            .campaignId(campaign.getId().toString())
                            .campaignName(campaign.getName())
                            .status(campaign.getStatus().name())
                            .targetCount(targetCount)
                            .emailsOpened(opened)
                            .linksClicked(clicked)
                            .credentialsSubmitted(submitted)
                            .downloadTriggered(downloadTriggered)
                            .consentGranted(consentGranted)
                            .actionsTaken(actionsTaken)
                            .successRate(successRate)
                            .build();
                })
                .toList();

        DashboardResponse response = DashboardResponse.builder()
                .totalEmployees(totalEmployees)
                .totalCampaigns(totalCampaigns)
                .activeCampaigns(activeCampaigns)
                .overallPhishingRate(overallPhishingRate)
                .departmentStats(departmentStats)
                .recentCampaigns(recentCampaigns)
                .build();
                
        log.info("Calculated dashboard stats for company {}: total employees={}, active campaigns={}", 
                companyId, totalEmployees, activeCampaigns);
                
        return response;
    }

    @Override
    public CampaignFunnelResponse getCampaignFunnel(UUID companyId, UUID campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .filter(c -> c.getCompanyId().equals(companyId) && !c.isDeleted())
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found"));

        List<TrackingEvent> events = trackingEventRepository.findAllByCampaignId(campaignId);

        long opened = events.stream().filter(e -> e.getEventType() == TrackingEventType.EMAIL_OPENED).count();
        long clicked = events.stream().filter(e -> e.getEventType() == TrackingEventType.LINK_CLICKED).count();
        long submitted = events.stream().filter(e -> e.getEventType() == TrackingEventType.CREDENTIALS_SUBMITTED).count();
        long downloadTriggered = events.stream().filter(e -> e.getEventType() == TrackingEventType.DOWNLOAD_TRIGGERED).count();
        long consentGranted = events.stream().filter(e -> e.getEventType() == TrackingEventType.CONSENT_GRANTED).count();
        long actionsTaken = events.stream().filter(e -> isRiskActionEvent(e.getEventType())).count();

        long targetCount = campaign.getTargetCount();
        
        double openRate = targetCount > 0 ? (double) opened / targetCount * 100 : 0;
        double clickRate = targetCount > 0 ? (double) clicked / targetCount * 100 : 0;
        double submitRate = targetCount > 0 ? (double) submitted / targetCount * 100 : 0;
        double actionRate = targetCount > 0 ? (double) actionsTaken / targetCount * 100 : 0;

        return CampaignFunnelResponse.builder()
                .campaignId(campaignId)
                .targetCount(targetCount)
                .emailsDelivered(targetCount)
                .emailsOpened(opened)
                .linksClicked(clicked)
                .credentialsSubmitted(submitted)
                .downloadTriggered(downloadTriggered)
                .consentGranted(consentGranted)
                .actionsTaken(actionsTaken)
                .openRate(openRate)
                .clickRate(clickRate)
                .submitRate(submitRate)
                .actionRate(actionRate)
                .build();
    }

    @Override
    public List<TrackingEventResponse> getCampaignEvents(UUID companyId, UUID campaignId) {
        // Validate campaign belongs to company
        campaignRepository.findById(campaignId)
                .filter(c -> c.getCompanyId().equals(companyId) && !c.isDeleted())
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found"));

        List<TrackingEvent> events = trackingEventRepository.findAllByCampaignId(campaignId);
        
        // Fetch employees to enrich event data
        Set<UUID> employeeIds = events.stream().map(TrackingEvent::getEmployeeId).collect(Collectors.toSet());
        Map<UUID, Employee> employeeMap = employeeRepository.findAllById(employeeIds).stream()
                .collect(Collectors.toMap(Employee::getId, e -> e));

        return events.stream()
                .sorted(Comparator.comparing(TrackingEvent::getOccurredAt).reversed())
                .map(event -> {
                    Employee employee = employeeMap.get(event.getEmployeeId());
                    String name = employee != null ? employee.getFirstName() + " " + employee.getLastName() : "Unknown";
                    String department = employee != null ? employee.getDepartment() : "Unknown";
                    
                    return TrackingEventResponse.builder()
                            .eventId(event.getId())
                            .employeeId(event.getEmployeeId())
                            .employeeName(name)
                            .employeeDepartment(department)
                            .eventType(event.getEventType())
                            .occurredAt(event.getOccurredAt())
                            .build();
                })
                .toList();
    }

    private boolean isRiskActionEvent(TrackingEventType eventType) {
        return eventType == TrackingEventType.LINK_CLICKED
                || eventType == TrackingEventType.CREDENTIALS_SUBMITTED
                || eventType == TrackingEventType.DOWNLOAD_TRIGGERED
                || eventType == TrackingEventType.CONSENT_GRANTED;
    }
}
