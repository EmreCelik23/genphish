package com.genphish.campaign.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.genphish.campaign.dto.request.CreateCampaignRequest;
import com.genphish.campaign.dto.request.ScheduleCampaignRequest;
import com.genphish.campaign.dto.response.CampaignResponse;
import com.genphish.campaign.entity.enums.CampaignStatus;
import com.genphish.campaign.entity.enums.TargetingType;
import com.genphish.campaign.service.CampaignService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CampaignControllerTest {

    @Mock
    private CampaignService campaignService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private UUID companyId;
    private UUID campaignId;

    @BeforeEach
    void setUp() {
        CampaignController controller = new CampaignController(campaignService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        objectMapper = new ObjectMapper();
        
        companyId = UUID.randomUUID();
        campaignId = UUID.randomUUID();
    }

    @Test
    void createCampaign_ShouldReturnCreated() throws Exception {
        CreateCampaignRequest request = new CreateCampaignRequest();
        request.setName("Q2 Security Drill");
        request.setTargetingType(TargetingType.ALL_COMPANY);
        request.setTemplateId(UUID.randomUUID());

        CampaignResponse response = CampaignResponse.builder()
                .id(campaignId)
                .companyId(companyId)
                .name("Q2 Security Drill")
                .targetingType(TargetingType.ALL_COMPANY)
                .status(CampaignStatus.READY)
                .build();

        when(campaignService.createCampaign(eq(companyId), any(CreateCampaignRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/companies/{companyId}/campaigns", companyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(campaignId.toString()))
                .andExpect(jsonPath("$.name").value("Q2 Security Drill"));
    }

    @Test
    void getAllCampaigns_ShouldReturnList() throws Exception {
        CampaignResponse response = CampaignResponse.builder()
                .id(campaignId)
                .name("Campaign 1")
                .status(CampaignStatus.READY)
                .build();

        when(campaignService.getAllCampaigns(companyId)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/companies/{companyId}/campaigns", companyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(campaignId.toString()))
                .andExpect(jsonPath("$[0].name").value("Campaign 1"));
    }

    @Test
    void getCampaignById_ShouldReturnCampaign() throws Exception {
        CampaignResponse response = CampaignResponse.builder()
                .id(campaignId)
                .name("Campaign 1")
                .status(CampaignStatus.READY)
                .build();

        when(campaignService.getCampaignById(companyId, campaignId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/companies/{companyId}/campaigns/{campaignId}", companyId, campaignId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(campaignId.toString()));
    }

    @Test
    void startCampaign_ShouldReturnStartedCampaign() throws Exception {
        CampaignResponse response = CampaignResponse.builder()
                .id(campaignId)
                .name("Launch")
                .status(CampaignStatus.IN_PROGRESS)
                .build();

        when(campaignService.startCampaign(companyId, campaignId)).thenReturn(response);

        mockMvc.perform(post("/api/v1/companies/{companyId}/campaigns/{campaignId}/start", companyId, campaignId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void scheduleCampaign_ShouldReturnScheduledCampaign() throws Exception {
        String requestJson = "{\"scheduledFor\":\"2030-01-01T10:00:00\"}";

        CampaignResponse response = CampaignResponse.builder()
                .id(campaignId)
                .status(CampaignStatus.SCHEDULED)
                .build();

        when(campaignService.scheduleCampaign(eq(companyId), eq(campaignId), any(ScheduleCampaignRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/companies/{companyId}/campaigns/{campaignId}/schedule", companyId, campaignId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCHEDULED"));
    }

    @Test
    void cancelCampaign_ShouldReturnCanceledCampaign() throws Exception {
        CampaignResponse response = CampaignResponse.builder()
                .id(campaignId)
                .status(CampaignStatus.CANCELED)
                .build();

        when(campaignService.cancelCampaign(companyId, campaignId)).thenReturn(response);

        mockMvc.perform(post("/api/v1/companies/{companyId}/campaigns/{campaignId}/cancel", companyId, campaignId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"));
    }

    @Test
    void deleteCampaign_ShouldReturnNoContent() throws Exception {
        mockMvc.perform(delete("/api/v1/companies/{companyId}/campaigns/{campaignId}", companyId, campaignId))
                .andExpect(status().isNoContent());

        verify(campaignService).deleteCampaign(companyId, campaignId);
    }
}
