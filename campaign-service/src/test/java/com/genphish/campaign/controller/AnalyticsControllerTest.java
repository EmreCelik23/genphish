package com.genphish.campaign.controller;

import com.genphish.campaign.dto.response.DashboardResponse;
import com.genphish.campaign.service.AnalyticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AnalyticsControllerTest {

    @Mock
    private AnalyticsService analyticsService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AnalyticsController controller = new AnalyticsController(analyticsService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void getDashboard_ShouldReturnDashboardData() throws Exception {
        UUID companyId = UUID.randomUUID();

        DashboardResponse response = DashboardResponse.builder()
                .totalEmployees(12)
                .totalCampaigns(4)
                .activeCampaigns(1)
                .overallPhishingRate(25.0)
                .departmentStats(List.of())
                .recentCampaigns(List.of())
                .build();

        when(analyticsService.getDashboard(companyId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/companies/{companyId}/analytics/dashboard", companyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEmployees").value(12))
                .andExpect(jsonPath("$.overallPhishingRate").value(25.0));
    }
}
