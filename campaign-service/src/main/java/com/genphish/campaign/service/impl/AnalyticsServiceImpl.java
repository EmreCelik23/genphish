package com.genphish.campaign.service.impl;

import com.genphish.campaign.dto.response.DashboardResponse;
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
                .filter(e -> e.getEventType() == TrackingEventType.CREDENTIALS_SUBMITTED)
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

                    long deptPhished = deptEvents.stream()
                            .filter(e -> e.getEventType() == TrackingEventType.CREDENTIALS_SUBMITTED
                                    || e.getEventType() == TrackingEventType.LINK_CLICKED)
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

                    // Real target count from campaign launch
                    long targetCount = campaign.getTargetCount();

                    long safeEmployees = targetCount - campaignEvents.stream()
                            .filter(e -> e.getEventType() == TrackingEventType.CREDENTIALS_SUBMITTED)
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
}
