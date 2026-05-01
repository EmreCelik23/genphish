package com.genphish.campaign.messaging.producer;

import com.genphish.campaign.config.KafkaConfig;
import com.genphish.campaign.entity.Campaign;
import com.genphish.campaign.entity.Employee;
import com.genphish.campaign.entity.PhishingTemplate;
import com.genphish.campaign.entity.enums.LanguageCode;
import com.genphish.campaign.entity.enums.TemplateCategory;
import com.genphish.campaign.messaging.event.EmailDeliveryEvent;
import com.genphish.campaign.repository.CampaignRepository;
import com.genphish.campaign.repository.CampaignTargetRepository;
import com.genphish.campaign.repository.EmployeeRepository;
import com.genphish.campaign.repository.PhishingTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailDeliveryProducer {

    private final KafkaTemplate<String, EmailDeliveryEvent> kafkaTemplate;
    private final EmployeeRepository employeeRepository;
    private final CampaignTargetRepository campaignTargetRepository;
    private final PhishingTemplateRepository phishingTemplateRepository;
    private final CampaignRepository campaignRepository;

    @Value("${app.tracker.base-url:http://localhost:8081}")
    private String trackerBaseUrl;

    @Value("${app.campaign.high-risk-threshold:70.0}")
    private Double highRiskThreshold;

    private static final String BODY_CLOSING_TAG = "</body>";

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
        PhishingTemplate template = phishingTemplateRepository.findByIdAndIsActive(campaign.getTemplateId(), true)
                .orElse(null);

        if (template == null || template.getEmailSubject() == null || template.getEmailBody() == null) {
            log.error("Aborting email delivery and setting campaign {} to FAILED. Reason: Template not found or incomplete.", campaign.getId());
            campaign.setStatus(com.genphish.campaign.entity.enums.CampaignStatus.FAILED);
            campaignRepository.save(campaign);
            return;
        }

        String emailSubject = template.getEmailSubject();
        String emailBodyHtml = template.getEmailBody();
        String languageCode = template.getLanguageCode() != null
                ? template.getLanguageCode().name()
                : LanguageCode.TR.name();
        TemplateCategory templateCategory = template.getTemplateCategory() != null
                ? template.getTemplateCategory()
                : TemplateCategory.CREDENTIAL_HARVESTING;
        if (templateCategory == TemplateCategory.OAUTH_CONSENT
                && (template.getTargetUrl() == null || template.getTargetUrl().isBlank())) {
            log.error("Aborting email delivery and setting campaign {} to FAILED. Reason: OAUTH_CONSENT template requires targetUrl.", campaign.getId());
            campaign.setStatus(com.genphish.campaign.entity.enums.CampaignStatus.FAILED);
            campaignRepository.save(campaign);
            return;
        }

        // 3. Assemble Fat Events and Push to Kafka
        for (Employee emp : targets) {
            String trackingPixelUrl = String.format(
                    "%s/track/open?c=%s&e=%s&co=%s&lang=%s",
                    trackerBaseUrl, campaign.getId(), emp.getId(), campaign.getCompanyId(), languageCode
            );
            String phishingLinkUrl = templateCategory == TemplateCategory.OAUTH_CONSENT
                    ? buildOAuthConsentLink(template.getTargetUrl(), campaign.getId(), emp.getId(), campaign.getCompanyId(), languageCode)
                    : String.format(
                        "%s/track/click?c=%s&e=%s&co=%s&lang=%s&tc=%s",
                        trackerBaseUrl,
                        campaign.getId(),
                        emp.getId(),
                        campaign.getCompanyId(),
                        languageCode,
                        templateCategory.name()
                    );

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
                    .qrCodeEnabled(campaign.isQrCodeEnabled())
                    .build();

            sendSingleDelivery(event);
        }
        
        log.info("Finished pushing {} email delivery events for campaign: {}", targets.size(), campaign.getId());
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

    private String buildOAuthConsentLink(
            String targetUrl,
            java.util.UUID campaignId,
            java.util.UUID employeeId,
            java.util.UUID companyId,
            String languageCode
    ) {
        String state = buildOAuthState(campaignId, employeeId, companyId, languageCode);
        try {
            return UriComponentsBuilder.fromUriString(targetUrl)
                    .replaceQueryParam("state", state)
                    .build(true)
                    .toUriString();
        } catch (Exception e) {
            log.warn("Failed to build oauth consent URL with state; using raw target URL.", e);
            return targetUrl;
        }
    }

    private String buildOAuthState(
            java.util.UUID campaignId,
            java.util.UUID employeeId,
            java.util.UUID companyId,
            String languageCode
    ) {
        String payload = String.format("c=%s&e=%s&co=%s&lang=%s", campaignId, employeeId, companyId, languageCode);
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }
}
