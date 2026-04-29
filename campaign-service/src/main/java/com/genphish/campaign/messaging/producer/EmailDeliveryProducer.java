package com.genphish.campaign.messaging.producer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.genphish.campaign.client.PythonServiceClient;
import com.genphish.campaign.config.KafkaConfig;
import com.genphish.campaign.entity.Campaign;
import com.genphish.campaign.entity.Employee;
import com.genphish.campaign.entity.PhishingTemplate;
import com.genphish.campaign.messaging.event.EmailDeliveryEvent;
import com.genphish.campaign.repository.CampaignRepository;
import com.genphish.campaign.repository.CampaignTargetRepository;
import com.genphish.campaign.repository.EmployeeRepository;
import com.genphish.campaign.repository.PhishingTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailDeliveryProducer {

    private final KafkaTemplate<String, EmailDeliveryEvent> kafkaTemplate;
    private final EmployeeRepository employeeRepository;
    private final CampaignTargetRepository campaignTargetRepository;
    private final PhishingTemplateRepository phishingTemplateRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final PythonServiceClient pythonServiceClient;
    private final CampaignRepository campaignRepository;

    @Value("${app.tracker.base-url:http://localhost:8081}")
    private String trackerBaseUrl;

    @Value("${app.campaign.high-risk-threshold:70.0}")
    private Double highRiskThreshold;

    @Value("${app.redis.template-ttl-days:14}")
    private long templateTtlDays;

    private static final String BODY_CLOSING_TAG = "</body>";

    // Temporary DTO to parse JSON from Redis
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AiTemplateData(String subject, String bodyHtml, String landingPageCode) {}

    private record EmailTemplateContent(String subject, String bodyHtml) {}

    // Sends individual email delivery requests to Go service (Fat Event Pattern)
    public void sendDeliveryRequest(Campaign campaign) {
        log.info("Starting email delivery orchestrator for campaign: {}", campaign.getId());

        // 1. Fetch Target Employees
        List<Employee> targets = fetchTargetEmployees(campaign);
        if (targets.isEmpty()) {
            log.warn("No active targets found for campaign: {}", campaign.getId());
            return;
        }

        // 2. Fetch Template Content
        EmailTemplateContent templateContent;
        try {
            templateContent = resolveEmailTemplate(campaign);
        } catch (IllegalStateException e) {
            log.error("Aborting email delivery and setting campaign {} to FAILED. Reason: {}", campaign.getId(), e.getMessage());
            campaign.setStatus(com.genphish.campaign.entity.enums.CampaignStatus.FAILED);
            campaignRepository.save(campaign);
            return;
        }

        String emailSubject = templateContent.subject();
        String emailBodyHtml = templateContent.bodyHtml();

        // 3. Assemble Fat Events and Push to Kafka
        for (Employee emp : targets) {
            String trackingPixelUrl = String.format("%s/track/open?c=%s&e=%s", trackerBaseUrl, campaign.getId(), emp.getId());
            String phishingLinkUrl = String.format("%s/track/click?c=%s&e=%s", trackerBaseUrl, campaign.getId(), emp.getId());

            // Prepare the HTML content by replacing tags like {{name}} or {{department}}.
            String personalizedHtml = emailBodyHtml
                    .replace("{{name}}", emp.getFirstName())
                    .replace("{{department}}", emp.getDepartment() != null ? emp.getDepartment() : "")
                    .replace("{{phishing_link}}", phishingLinkUrl);

            // Insert invisible tracking pixel (1x1 image) just before </body>
            String trackingPixelImg = String.format("<img src=\"%s\" width=\"1\" height=\"1\" style=\"display:none;\" />", trackingPixelUrl);
            if (personalizedHtml.contains(BODY_CLOSING_TAG)) {
                personalizedHtml = personalizedHtml.replace(BODY_CLOSING_TAG, trackingPixelImg + BODY_CLOSING_TAG);
            } else {
                personalizedHtml += trackingPixelImg;
            }

            EmailDeliveryEvent event = EmailDeliveryEvent.builder()
                    .campaignId(campaign.getId())
                    .companyId(campaign.getCompanyId())
                    .employeeId(emp.getId())
                    .recipientName(emp.getFirstName() + " " + emp.getLastName())
                    .recipientEmail(emp.getEmail())
                    .department(emp.getDepartment())
                    .emailSubject(emailSubject)
                    .emailBodyHtml(personalizedHtml) // Now fully prepared with tracking pixels and personalized links
                    .trackingPixelUrl(trackingPixelUrl)
                    .phishingLinkUrl(phishingLinkUrl)
                    .qrCodeEnabled(false) // Assuming standard campaign
                    .build();

            sendSingleDelivery(event);
        }
        
        log.info("Finished pushing {} email delivery events for campaign: {}", targets.size(), campaign.getId());
    }

    private EmailTemplateContent resolveEmailTemplate(Campaign campaign) {
        if (campaign.isAiGenerated()) {
            String redisKey = "ai_template:" + campaign.getMongoTemplateId();
            String jsonContent = redisTemplate.opsForValue().get(redisKey);

            if (jsonContent == null || jsonContent.isBlank()) {
                log.warn("Cache Miss: AI template not found in Redis for campaign {}. Attempting to fetch from Python service...", campaign.getId());
                jsonContent = pythonServiceClient.getTemplateById(campaign.getMongoTemplateId());
                
                if (jsonContent != null && !jsonContent.isBlank()) {
                    // Repopulate cache
                    redisTemplate.opsForValue().set(redisKey, jsonContent, templateTtlDays, java.util.concurrent.TimeUnit.DAYS);
                    log.info("Cache-Aside: Successfully fetched and cached AI template for campaign {}", campaign.getId());
                } else {
                    log.error("Cache Miss Fallback FAILED: Template permanently lost for campaign: {}", campaign.getId());
                    throw new IllegalStateException("AI template permanently lost");
                }
            }

            try {
                AiTemplateData aiData = objectMapper.readValue(jsonContent, AiTemplateData.class);
                return new EmailTemplateContent(aiData.subject(), aiData.bodyHtml());
            } catch (Exception e) {
                log.error("Failed to parse AI template for campaign: {}", campaign.getId(), e);
                throw new IllegalStateException("AI template parsing failed", e);
            }
        } else {
            // Static template fallback
            PhishingTemplate template = phishingTemplateRepository.findByIdAndIsActive(campaign.getStaticTemplateId(), true)
                    .orElseThrow(() -> new IllegalStateException("Static template not found or inactive for campaign: " + campaign.getId()));
            return new EmailTemplateContent(template.getEmailSubject(), template.getEmailBody());
        }
    }

    private List<Employee> fetchTargetEmployees(Campaign campaign) {
        return switch (campaign.getTargetingType()) {
            case ALL_COMPANY -> employeeRepository.findAllByCompanyIdAndIsActive(campaign.getCompanyId(), true);
            case DEPARTMENT -> employeeRepository.findAllByCompanyIdAndDepartmentAndIsActive(campaign.getCompanyId(), campaign.getTargetDepartment(), true);
            case INDIVIDUAL -> {
                List<java.util.UUID> empIds = campaignTargetRepository.findAllByCampaignId(campaign.getId()).stream()
                        .map(com.genphish.campaign.entity.CampaignTarget::getEmployeeId)
                        .toList();
                yield employeeRepository.findAllById(empIds).stream()
                        .filter(e -> e.isActive() && e.getCompanyId().equals(campaign.getCompanyId()))
                        .toList();
            }
            case HIGH_RISK -> employeeRepository.findAllByCompanyIdAndRiskScoreGreaterThanEqualAndIsActive(campaign.getCompanyId(), highRiskThreshold, true);
        };
    }

    private void sendSingleDelivery(EmailDeliveryEvent event) {
        kafkaTemplate.send(
                KafkaConfig.TOPIC_EMAIL_DELIVERY_QUEUE,
                event.getCampaignId().toString(), // Partition key
                event
        );
        log.debug("Sent email delivery event for employee {} in campaign {}", event.getEmployeeId(), event.getCampaignId());
    }
}
