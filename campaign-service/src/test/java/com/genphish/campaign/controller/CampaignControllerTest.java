package com.genphish.campaign.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.genphish.campaign.dto.request.CreateCampaignRequest;
import com.genphish.campaign.dto.request.RegenerateAiCampaignRequest;
import com.genphish.campaign.dto.response.CampaignResponse;
import com.genphish.campaign.entity.enums.CampaignStatus;
import com.genphish.campaign.entity.enums.DifficultyLevel;
import com.genphish.campaign.entity.enums.LanguageCode;
import com.genphish.campaign.entity.enums.RegenerationScope;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CampaignControllerTest {

    @Mock
    private CampaignService campaignService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        CampaignController controller = new CampaignController(campaignService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void createCampaign_ShouldReturnCreated() throws Exception {
        UUID companyId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();

        CreateCampaignRequest request = new CreateCampaignRequest();
        request.setName("Q2 Security Drill");
        request.setTargetingType(TargetingType.ALL_COMPANY);
        request.setIsAiGenerated(true);
        request.setAiPrompt("Generate a password reset campaign");
        request.setTargetUrl("https://portal.example.com");
        request.setDifficultyLevel(DifficultyLevel.PROFESSIONAL);
        request.setLanguageCode("EN");
        request.setAiProvider("openai");
        request.setAiModel("gpt-4o-mini");

        CampaignResponse response = CampaignResponse.builder()
                .id(campaignId)
                .companyId(companyId)
                .name("Q2 Security Drill")
                .targetingType(TargetingType.ALL_COMPANY)
                .isAiGenerated(true)
                .languageCode(LanguageCode.EN)
                .aiProvider("openai")
                .aiModel("gpt-4o-mini")
                .status(CampaignStatus.GENERATING)
                .build();

        when(campaignService.createCampaign(eq(companyId), any(CreateCampaignRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/companies/{companyId}/campaigns", companyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(campaignId.toString()))
                .andExpect(jsonPath("$.name").value("Q2 Security Drill"))
                .andExpect(jsonPath("$.languageCode").value("EN"))
                .andExpect(jsonPath("$.aiProvider").value("openai"))
                .andExpect(jsonPath("$.aiModel").value("gpt-4o-mini"));
    }

    @Test
    void getAllCampaigns_ShouldReturnList() throws Exception {
        UUID companyId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();

        CampaignResponse response = CampaignResponse.builder()
                .id(campaignId)
                .companyId(companyId)
                .name("Campaign 1")
                .targetingType(TargetingType.ALL_COMPANY)
                .isAiGenerated(false)
                .status(CampaignStatus.DRAFT)
                .build();

        when(campaignService.getAllCampaigns(companyId)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/companies/{companyId}/campaigns", companyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(campaignId.toString()))
                .andExpect(jsonPath("$[0].name").value("Campaign 1"));
    }

    @Test
    void getCampaignById_ShouldReturnCampaign() throws Exception {
        UUID companyId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();

        CampaignResponse response = CampaignResponse.builder()
                .id(campaignId)
                .companyId(companyId)
                .name("Campaign 1")
                .targetingType(TargetingType.ALL_COMPANY)
                .isAiGenerated(false)
                .status(CampaignStatus.DRAFT)
                .build();

        when(campaignService.getCampaignById(companyId, campaignId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/companies/{companyId}/campaigns/{campaignId}", companyId, campaignId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(campaignId.toString()));
    }

    @Test
    void regenerateAiContent_ShouldReturnUpdatedCampaign() throws Exception {
        UUID companyId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();

        RegenerateAiCampaignRequest request = RegenerateAiCampaignRequest.builder()
                .scope(RegenerationScope.ONLY_EMAIL)
                .newPrompt("Update subject only")
                .build();

        CampaignResponse response = CampaignResponse.builder()
                .id(campaignId)
                .companyId(companyId)
                .name("AI Campaign")
                .targetingType(TargetingType.ALL_COMPANY)
                .isAiGenerated(true)
                .status(CampaignStatus.GENERATING)
                .build();

        when(campaignService.regenerateAiContent(eq(companyId), eq(campaignId), any(RegenerateAiCampaignRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/companies/{companyId}/campaigns/{campaignId}/regenerate", companyId, campaignId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(campaignId.toString()))
                .andExpect(jsonPath("$.status").value("GENERATING"));
    }

    @Test
    void startCampaign_ShouldReturnStartedCampaign() throws Exception {
        UUID companyId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();

        CampaignResponse response = CampaignResponse.builder()
                .id(campaignId)
                .companyId(companyId)
                .name("Launch")
                .targetingType(TargetingType.ALL_COMPANY)
                .isAiGenerated(false)
                .status(CampaignStatus.IN_PROGRESS)
                .build();

        when(campaignService.startCampaign(companyId, campaignId)).thenReturn(response);

        mockMvc.perform(post("/api/v1/companies/{companyId}/campaigns/{campaignId}/start", companyId, campaignId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void deleteCampaign_ShouldReturnNoContent() throws Exception {
        UUID companyId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/companies/{companyId}/campaigns/{campaignId}", companyId, campaignId))
                .andExpect(status().isNoContent());

        verify(campaignService).deleteCampaign(companyId, campaignId);
    }
}
