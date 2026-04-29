package com.genphish.campaign.messaging.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.genphish.campaign.client.PythonServiceClient;
import com.genphish.campaign.config.KafkaConfig;
import com.genphish.campaign.entity.Campaign;
import com.genphish.campaign.entity.Employee;
import com.genphish.campaign.entity.PhishingTemplate;
import com.genphish.campaign.entity.enums.CampaignStatus;
import com.genphish.campaign.entity.enums.TargetingType;
import com.genphish.campaign.messaging.event.EmailDeliveryEvent;
import com.genphish.campaign.repository.CampaignRepository;
import com.genphish.campaign.repository.CampaignTargetRepository;
import com.genphish.campaign.repository.EmployeeRepository;
import com.genphish.campaign.repository.PhishingTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailDeliveryProducerTest {

    @Mock
    private KafkaTemplate<String, EmailDeliveryEvent> kafkaTemplate;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private CampaignTargetRepository campaignTargetRepository;
    @Mock
    private PhishingTemplateRepository phishingTemplateRepository;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private PythonServiceClient pythonServiceClient;
    @Mock
    private CampaignRepository campaignRepository;
    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private EmailDeliveryProducer producer;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(producer, "trackerBaseUrl", "http://tracker.local");
        ReflectionTestUtils.setField(producer, "highRiskThreshold", 70.0);
        ReflectionTestUtils.setField(producer, "templateTtlDays", 14L);
    }

    @Test
    void sendDeliveryRequest_WhenNoTargets_ShouldReturnWithoutPublishing() {
        Campaign campaign = Campaign.builder()
                .id(UUID.randomUUID())
                .companyId(UUID.randomUUID())
                .targetingType(TargetingType.ALL_COMPANY)
                .isAiGenerated(false)
                .build();

        when(employeeRepository.findAllByCompanyIdAndIsActive(campaign.getCompanyId(), true)).thenReturn(List.of());

        producer.sendDeliveryRequest(campaign);

        verify(kafkaTemplate, never()).send(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(EmailDeliveryEvent.class)
        );
        verify(campaignRepository, never()).save(org.mockito.ArgumentMatchers.any(Campaign.class));
    }

    @Test
    void sendDeliveryRequest_WhenStaticTemplate_ShouldPersonalizeAndPublish() {
        UUID campaignId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();

        Campaign campaign = Campaign.builder()
                .id(campaignId)
                .companyId(companyId)
                .targetingType(TargetingType.ALL_COMPANY)
                .isAiGenerated(false)
                .staticTemplateId(templateId)
                .qrCodeEnabled(true)
                .build();

        Employee employee = Employee.builder()
                .id(employeeId)
                .companyId(companyId)
                .firstName("Ayse")
                .lastName("Yilmaz")
                .email("ayse@example.com")
                .department("IT")
                .isActive(true)
                .build();

        PhishingTemplate template = PhishingTemplate.builder()
                .id(templateId)
                .emailSubject("Critical security update")
                .emailBody("<html><body>Hello {{name}} from {{department}}. Click {{phishing_link}}</body></html>")
                .isActive(true)
                .build();

        when(employeeRepository.findAllByCompanyIdAndIsActive(companyId, true)).thenReturn(List.of(employee));
        when(phishingTemplateRepository.findByIdAndIsActive(templateId, true)).thenReturn(Optional.of(template));

        producer.sendDeliveryRequest(campaign);

        ArgumentCaptor<EmailDeliveryEvent> eventCaptor = ArgumentCaptor.forClass(EmailDeliveryEvent.class);
        verify(kafkaTemplate).send(
                eq(KafkaConfig.TOPIC_EMAIL_DELIVERY_QUEUE),
                eq(campaignId.toString()),
                eventCaptor.capture()
        );

        EmailDeliveryEvent event = eventCaptor.getValue();
        assertThat(event.getCampaignId()).isEqualTo(campaignId);
        assertThat(event.getEmployeeId()).isEqualTo(employeeId);
        assertThat(event.getRecipientEmail()).isEqualTo("ayse@example.com");
        assertThat(event.getEmailSubject()).isEqualTo("Critical security update");
        assertThat(event.getTrackingPixelUrl()).contains("http://tracker.local/track/open");
        assertThat(event.getPhishingLinkUrl()).contains("http://tracker.local/track/click");
        assertThat(event.getEmailBodyHtml()).contains("Hello Ayse from IT");
        assertThat(event.getEmailBodyHtml()).doesNotContain("{{name}}", "{{department}}", "{{phishing_link}}");
        assertThat(event.getEmailBodyHtml()).contains("<img src=\"http://tracker.local/track/open?c=");
        assertThat(event.isQrCodeEnabled()).isTrue();
    }

    @Test
    void sendDeliveryRequest_WhenAiTemplateMissingEverywhere_ShouldMarkCampaignFailed() {
        UUID campaignId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();

        Campaign campaign = Campaign.builder()
                .id(campaignId)
                .companyId(companyId)
                .targetingType(TargetingType.ALL_COMPANY)
                .isAiGenerated(true)
                .mongoTemplateId("missing-mongo-template")
                .status(CampaignStatus.IN_PROGRESS)
                .build();

        Employee employee = Employee.builder()
                .id(employeeId)
                .companyId(companyId)
                .firstName("Ali")
                .lastName("Kaya")
                .email("ali@example.com")
                .department("HR")
                .isActive(true)
                .build();

        when(employeeRepository.findAllByCompanyIdAndIsActive(companyId, true)).thenReturn(List.of(employee));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("ai_template:missing-mongo-template")).thenReturn(null);
        when(pythonServiceClient.getTemplateById("missing-mongo-template")).thenReturn(null);

        producer.sendDeliveryRequest(campaign);

        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.FAILED);
        verify(campaignRepository).save(campaign);
        verify(kafkaTemplate, never()).send(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(EmailDeliveryEvent.class)
        );
    }
}
