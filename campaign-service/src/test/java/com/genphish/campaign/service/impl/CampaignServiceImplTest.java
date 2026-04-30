package com.genphish.campaign.service.impl;

import com.genphish.campaign.dto.request.CreateCampaignRequest;
import com.genphish.campaign.dto.response.CampaignResponse;
import com.genphish.campaign.entity.Campaign;
import com.genphish.campaign.entity.PhishingTemplate;
import com.genphish.campaign.entity.enums.CampaignStatus;
import com.genphish.campaign.entity.enums.TargetingType;
import com.genphish.campaign.entity.enums.TemplateStatus;
import com.genphish.campaign.exception.InvalidOperationException;
import com.genphish.campaign.messaging.producer.EmailDeliveryProducer;
import com.genphish.campaign.repository.CampaignRepository;
import com.genphish.campaign.repository.CampaignTargetRepository;
import com.genphish.campaign.repository.EmployeeRepository;
import com.genphish.campaign.repository.PhishingTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CampaignServiceImplTest {

    @Mock
    private CampaignRepository campaignRepository;
    @Mock
    private CampaignTargetRepository campaignTargetRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private PhishingTemplateRepository phishingTemplateRepository;
    @Mock
    private EmailDeliveryProducer emailDeliveryProducer;
    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private CampaignServiceImpl campaignService;

    private UUID companyId;
    private UUID campaignId;
    private UUID templateId;
    private PhishingTemplate template;

    @BeforeEach
    void setUp() {
        companyId = UUID.randomUUID();
        campaignId = UUID.randomUUID();
        templateId = UUID.randomUUID();

        ReflectionTestUtils.setField(campaignService, "highRiskThreshold", 70.0);

        template = PhishingTemplate.builder()
                .id(templateId)
                .companyId(companyId)
                .status(TemplateStatus.READY)
                .isActive(true)
                .build();
    }

    @Test
    void createCampaign_Success() {
        CreateCampaignRequest request = new CreateCampaignRequest();
        request.setName("Test Campaign");
        request.setTargetingType(TargetingType.ALL_COMPANY);
        request.setTemplateId(templateId);

        Campaign savedCampaign = Campaign.builder()
                .id(campaignId)
                .companyId(companyId)
                .name("Test Campaign")
                .targetingType(TargetingType.ALL_COMPANY)
                .templateId(templateId)
                .status(CampaignStatus.READY)
                .build();

        when(phishingTemplateRepository.findByIdAndIsActive(templateId, true))
                .thenReturn(Optional.of(template));
        when(campaignRepository.save(any(Campaign.class))).thenReturn(savedCampaign);

        CampaignResponse response = campaignService.createCampaign(companyId, request);

        assertNotNull(response);
        assertEquals(campaignId, response.getId());
        assertEquals(CampaignStatus.READY, response.getStatus());
        verify(campaignRepository).save(any(Campaign.class));
    }

    @Test
    void createCampaign_ThrowsException_WhenTemplateNotReady() {
        template.setStatus(TemplateStatus.GENERATING);
        CreateCampaignRequest request = new CreateCampaignRequest();
        request.setTemplateId(templateId);

        when(phishingTemplateRepository.findByIdAndIsActive(templateId, true))
                .thenReturn(Optional.of(template));

        assertThrows(InvalidOperationException.class, () -> campaignService.createCampaign(companyId, request));
    }

    @Test
    void startCampaign_Success() {
        Campaign campaign = Campaign.builder()
                .id(campaignId)
                .companyId(companyId)
                .status(CampaignStatus.READY)
                .targetingType(TargetingType.ALL_COMPANY)
                .build();

        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));
        when(employeeRepository.countByCompanyIdAndIsActive(companyId, true)).thenReturn(50L);

        CampaignResponse response = campaignService.startCampaign(companyId, campaignId);

        assertEquals(CampaignStatus.IN_PROGRESS, response.getStatus());
        verify(campaignRepository).save(campaign);
        verify(emailDeliveryProducer).sendDeliveryRequest(campaign);
        assertEquals(50L, campaign.getTargetCount());
    }

    @Test
    void cancelCampaign_Success() {
        Campaign campaign = Campaign.builder()
                .id(campaignId)
                .companyId(companyId)
                .status(CampaignStatus.IN_PROGRESS)
                .build();

        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));

        CampaignResponse response = campaignService.cancelCampaign(companyId, campaignId);

        assertEquals(CampaignStatus.CANCELED, response.getStatus());
        verify(kafkaTemplate).send(anyString(), anyString(), any());
        verify(campaignRepository).save(campaign);
    }
}
