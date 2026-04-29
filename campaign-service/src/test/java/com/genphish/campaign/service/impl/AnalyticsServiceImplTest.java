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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceImplTest {

    @Mock
    private CampaignRepository campaignRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private TrackingEventRepository trackingEventRepository;

    @InjectMocks
    private AnalyticsServiceImpl analyticsService;

    private UUID companyId;

    @BeforeEach
    void setUp() {
        companyId = UUID.randomUUID();
    }

    @Test
    void getDashboard_ShouldCalculateStatsCorrectly() {
        // Given
        UUID emp1Id = UUID.randomUUID();
        UUID emp2Id = UUID.randomUUID();
        
        Employee emp1 = Employee.builder().id(emp1Id).department("IT").build();
        Employee emp2 = Employee.builder().id(emp2Id).department("HR").build();
        
        when(employeeRepository.countByCompanyIdAndIsActive(companyId, true)).thenReturn(2L);
        when(campaignRepository.countByCompanyIdAndIsDeleted(companyId, false)).thenReturn(5L);
        when(campaignRepository.countByCompanyIdAndStatusAndIsDeleted(companyId, CampaignStatus.IN_PROGRESS, false)).thenReturn(1L);
        
        when(employeeRepository.findAllByCompanyIdAndIsActive(companyId, true)).thenReturn(List.of(emp1, emp2));

        Campaign camp1 = Campaign.builder().id(UUID.randomUUID()).name("Test Camp").status(CampaignStatus.COMPLETED).targetCount(2).build();
        when(campaignRepository.findAllByCompanyIdAndIsDeleted(companyId, false)).thenReturn(List.of(camp1));

        TrackingEvent event1 = TrackingEvent.builder()
                .employeeId(emp1Id).campaignId(camp1.getId()).eventType(TrackingEventType.CREDENTIALS_SUBMITTED).build();
        TrackingEvent event2 = TrackingEvent.builder()
                .employeeId(emp2Id).campaignId(camp1.getId()).eventType(TrackingEventType.EMAIL_OPENED).build();
                
        when(trackingEventRepository.findAllByCompanyId(companyId)).thenReturn(List.of(event1, event2));

        // When
        DashboardResponse dashboard = analyticsService.getDashboard(companyId);

        // Then
        assertThat(dashboard.getTotalEmployees()).isEqualTo(2L);
        assertThat(dashboard.getTotalCampaigns()).isEqualTo(5L);
        assertThat(dashboard.getActiveCampaigns()).isEqualTo(1L);
        
        // Overall Phishing Rate: 1 out of 2 employees phished = 50%
        assertThat(dashboard.getOverallPhishingRate()).isEqualTo(50.0);
        
        // Department Stats
        assertThat(dashboard.getDepartmentStats()).hasSize(2); // IT and HR
        
        DashboardResponse.DepartmentStats itStats = dashboard.getDepartmentStats().stream()
                .filter(d -> d.getDepartment().equals("IT")).findFirst().get();
        assertThat(itStats.getPhishingRate()).isEqualTo(100.0); // 1 IT employee, 1 phished
        assertThat(itStats.getCredentialsSubmitted()).isEqualTo(1);
        
        // Campaign Stats
        assertThat(dashboard.getRecentCampaigns()).hasSize(1);
        DashboardResponse.CampaignStats campStats = dashboard.getRecentCampaigns().get(0);
        assertThat(campStats.getCredentialsSubmitted()).isEqualTo(1);
        assertThat(campStats.getSuccessRate()).isEqualTo(50.0); // 2 targets, 1 phished -> 1 safe -> 50%
    }
}
