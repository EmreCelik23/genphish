package com.genphish.campaign.messaging.producer;

import com.genphish.campaign.config.KafkaConfig;
import com.genphish.campaign.entity.Campaign;
import com.genphish.campaign.entity.Employee;
import com.genphish.campaign.entity.PhishingTemplate;
import com.genphish.campaign.entity.enums.CampaignStatus;
import com.genphish.campaign.entity.enums.TargetingType;
import com.genphish.campaign.entity.enums.TemplateCategory;
import com.genphish.campaign.messaging.event.EmailDeliveryEvent;
import com.genphish.campaign.repository.CampaignRepository;
import com.genphish.campaign.repository.EmployeeRepository;
import com.genphish.campaign.repository.PhishingTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailDeliveryProducerTest {

    @Mock
    private KafkaTemplate<String, EmailDeliveryEvent> kafkaTemplate;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private PhishingTemplateRepository phishingTemplateRepository;
    @Mock
    private CampaignRepository campaignRepository;

    @InjectMocks
    private EmailDeliveryProducer producer;

    @Captor
    private ArgumentCaptor<EmailDeliveryEvent> eventCaptor;

    private UUID campaignId;
    private UUID companyId;
    private UUID templateId;
    private Campaign campaign;

    @BeforeEach
    void setUp() {
        campaignId = UUID.randomUUID();
        companyId = UUID.randomUUID();
        templateId = UUID.randomUUID();

        ReflectionTestUtils.setField(producer, "trackerBaseUrl", "http://localhost:8081");

        campaign = Campaign.builder()
                .id(campaignId)
                .companyId(companyId)
                .templateId(templateId)
                .targetingType(TargetingType.ALL_COMPANY)
                .build();
    }

    @Test
    void sendDeliveryRequest_Success() {
        Employee emp = Employee.builder()
                .id(UUID.randomUUID())
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .build();

        PhishingTemplate template = PhishingTemplate.builder()
                .id(templateId)
                .emailSubject("Urgent Security Update")
                .emailBody("<html>Hello {{name}}, click here: {{phishing_link}}</body></html>")
                .build();

        when(employeeRepository.findAllByCompanyIdAndIsActive(companyId, true))
                .thenReturn(List.of(emp));
        when(phishingTemplateRepository.findByIdAndIsActive(templateId, true))
                .thenReturn(Optional.of(template));

        producer.sendDeliveryRequest(campaign);

        verify(kafkaTemplate).send(
                eq(KafkaConfig.TOPIC_EMAIL_DELIVERY_QUEUE),
                eq(campaignId.toString()),
                eventCaptor.capture()
        );

        EmailDeliveryEvent sentEvent = eventCaptor.getValue();
        assertEquals("Urgent Security Update", sentEvent.getEmailSubject());
        assertTrue(sentEvent.getEmailBodyHtml().contains("Hello John"));
        assertTrue(sentEvent.getEmailBodyHtml().contains("http://localhost:8081/track/click"));
        assertTrue(sentEvent.getEmailBodyHtml().contains("exp="));
        assertTrue(sentEvent.getEmailBodyHtml().contains("sig="));
        assertTrue(sentEvent.getEmailBodyHtml().contains("<img src=\"http://localhost:8081/track/open"));
    }

    @Test
    void sendDeliveryRequest_Fails_WhenTemplateMissing() {
        when(employeeRepository.findAllByCompanyIdAndIsActive(companyId, true))
                .thenReturn(List.of(new Employee()));
        when(phishingTemplateRepository.findByIdAndIsActive(templateId, true))
                .thenReturn(Optional.empty());

        producer.sendDeliveryRequest(campaign);

        assertEquals(CampaignStatus.FAILED, campaign.getStatus());
        verify(campaignRepository).save(campaign);
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void sendDeliveryRequest_OAuthConsent_UsesTargetUrlWithState() {
        Employee emp = Employee.builder()
                .id(UUID.randomUUID())
                .firstName("Jane")
                .lastName("Doe")
                .email("jane@example.com")
                .build();

        PhishingTemplate template = PhishingTemplate.builder()
                .id(templateId)
                .templateCategory(TemplateCategory.OAUTH_CONSENT)
                .targetUrl("https://login.microsoftonline.com/common/oauth2/v2.0/authorize?client_id=test-client")
                .emailSubject("Microsoft consent request")
                .emailBody("<html>Open this: {{phishing_link}}</html>")
                .build();

        when(employeeRepository.findAllByCompanyIdAndIsActive(companyId, true))
                .thenReturn(List.of(emp));
        when(phishingTemplateRepository.findByIdAndIsActive(templateId, true))
                .thenReturn(Optional.of(template));

        producer.sendDeliveryRequest(campaign);

        verify(kafkaTemplate).send(
                eq(KafkaConfig.TOPIC_EMAIL_DELIVERY_QUEUE),
                eq(campaignId.toString()),
                eventCaptor.capture()
        );

        EmailDeliveryEvent sentEvent = eventCaptor.getValue();
        assertTrue(sentEvent.getEmailBodyHtml().contains("https://login.microsoftonline.com"));
        assertTrue(sentEvent.getEmailBodyHtml().contains("state="));
        assertTrue(sentEvent.getEmailBodyHtml().contains("."));
    }

    @Test
    void sendDeliveryRequest_OAuthConsent_Fails_WhenTargetUrlMissing() {
        Employee emp = Employee.builder()
                .id(UUID.randomUUID())
                .firstName("Jane")
                .lastName("Doe")
                .email("jane@example.com")
                .build();

        PhishingTemplate template = PhishingTemplate.builder()
                .id(templateId)
                .templateCategory(TemplateCategory.OAUTH_CONSENT)
                .targetUrl(" ")
                .emailSubject("Microsoft consent request")
                .emailBody("<html>Open this: {{phishing_link}}</html>")
                .build();

        when(employeeRepository.findAllByCompanyIdAndIsActive(companyId, true))
                .thenReturn(List.of(emp));
        when(phishingTemplateRepository.findByIdAndIsActive(templateId, true))
                .thenReturn(Optional.of(template));

        producer.sendDeliveryRequest(campaign);

        assertEquals(CampaignStatus.FAILED, campaign.getStatus());
        verify(campaignRepository).save(campaign);
        verifyNoInteractions(kafkaTemplate);
    }
}
