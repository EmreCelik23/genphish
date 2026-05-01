package com.genphish.campaign.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.genphish.campaign.dto.request.GenerateTemplateRequest;
import com.genphish.campaign.dto.request.RegenerateTemplateRequest;
import com.genphish.campaign.dto.request.UpdateTemplateRequest;
import com.genphish.campaign.dto.response.PhishingTemplateResponse;
import com.genphish.campaign.entity.enums.RegenerationScope;
import com.genphish.campaign.entity.enums.TemplateCategory;
import com.genphish.campaign.entity.enums.TemplateStatus;
import com.genphish.campaign.service.PhishingTemplateService;
import com.genphish.campaign.service.ReferenceImageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
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
class PhishingTemplateControllerTest {

    @Mock
    private PhishingTemplateService phishingTemplateService;

    @Mock
    private ReferenceImageService referenceImageService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private UUID companyId;
    private UUID templateId;

    @BeforeEach
    void setUp() {
        PhishingTemplateController controller = new PhishingTemplateController(phishingTemplateService, referenceImageService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        companyId = UUID.randomUUID();
        templateId = UUID.randomUUID();
    }

    @Test
    void getAllActiveTemplates_ShouldReturnList() throws Exception {
        PhishingTemplateResponse response = PhishingTemplateResponse.builder()
                .id(templateId)
                .name("Global Template")
                .status(TemplateStatus.READY)
                .build();

        when(phishingTemplateService.getAllActiveTemplates(companyId)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/companies/{companyId}/templates", companyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(templateId.toString()))
                .andExpect(jsonPath("$[0].name").value("Global Template"));
    }

    @Test
    void generateTemplate_ShouldReturnCreated() throws Exception {
        GenerateTemplateRequest request = new GenerateTemplateRequest();
        request.setName("New Generated Template");
        request.setCategory("AI");
        request.setPrompt("Some prompt");
        request.setTemplateCategory(TemplateCategory.CREDENTIAL_HARVESTING);

        PhishingTemplateResponse response = PhishingTemplateResponse.builder()
                .id(templateId)
                .name("New Generated Template")
                .status(TemplateStatus.GENERATING)
                .build();

        when(phishingTemplateService.generateAiTemplate(eq(companyId), any(GenerateTemplateRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/companies/{companyId}/templates/generate", companyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(templateId.toString()))
                .andExpect(jsonPath("$.status").value("GENERATING"));
    }

    @Test
    void updateTemplate_ShouldReturnUpdated() throws Exception {
        UpdateTemplateRequest request = new UpdateTemplateRequest();
        request.setName("Manual Update");
        request.setEmailSubject("Sub");
        request.setEmailBody("Body");

        PhishingTemplateResponse response = PhishingTemplateResponse.builder()
                .id(templateId)
                .name("Manual Update")
                .status(TemplateStatus.READY)
                .build();

        when(phishingTemplateService.updateTemplate(eq(companyId), eq(templateId), any(UpdateTemplateRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/v1/companies/{companyId}/templates/{templateId}", companyId, templateId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Manual Update"));
    }

    @Test
    void regenerateTemplate_ShouldReturnGenerating() throws Exception {
        RegenerateTemplateRequest request = new RegenerateTemplateRequest();
        request.setPrompt("Revise");
        request.setScope(RegenerationScope.ALL);

        PhishingTemplateResponse response = PhishingTemplateResponse.builder()
                .id(templateId)
                .status(TemplateStatus.GENERATING)
                .build();

        when(phishingTemplateService.regenerateAiTemplate(eq(companyId), eq(templateId), any(RegenerateTemplateRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/companies/{companyId}/templates/{templateId}/regenerate", companyId, templateId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("GENERATING"));
    }

    @Test
    void deleteTemplate_ShouldReturnNoContent() throws Exception {
        mockMvc.perform(delete("/api/v1/companies/{companyId}/templates/{templateId}", companyId, templateId))
                .andExpect(status().isNoContent());

        verify(phishingTemplateService).deleteTemplate(companyId, templateId);
    }

    @Test
    void uploadReferenceImage_ShouldReturnCreatedWithUrl() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "login.png",
                MediaType.IMAGE_PNG_VALUE,
                new byte[] {1, 2, 3}
        );
        when(referenceImageService.store(companyId, file))
                .thenReturn("http://localhost:8080/api/v1/reference-images/a.png");

        mockMvc.perform(multipart("/api/v1/companies/{companyId}/templates/upload-reference", companyId)
                        .file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.referenceImageUrl").value("http://localhost:8080/api/v1/reference-images/a.png"));
    }
}
