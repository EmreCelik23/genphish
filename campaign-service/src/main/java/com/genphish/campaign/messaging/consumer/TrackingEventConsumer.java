package com.genphish.campaign.messaging.consumer;

import com.genphish.campaign.config.KafkaConfig;
import com.genphish.campaign.entity.TrackingEvent;
import com.genphish.campaign.entity.enums.TrackingEventType;
import com.genphish.campaign.messaging.event.TrackingEventMessage;
import com.genphish.campaign.repository.EmployeeRepository;
import com.genphish.campaign.repository.TrackingEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
@RequiredArgsConstructor
@Slf4j
public class TrackingEventConsumer {

    private final TrackingEventRepository trackingEventRepository;
    private final EmployeeRepository employeeRepository;

    private static final double RISK_INCREMENT_EMAIL_OPENED = 5.0;
    private static final double RISK_INCREMENT_LINK_CLICKED = 15.0;
    private static final double RISK_INCREMENT_CREDENTIALS = 30.0;

    // Listens for tracking events from Go Tracker service
    @KafkaListener(
            topics = KafkaConfig.TOPIC_TRACKING_EVENTS,
            groupId = "campaign-service-group"
    )
    public void consume(TrackingEventMessage message) {
        log.info("Received tracking event: {} for employee {} in campaign {}",
                message.getEventType(), message.getEmployeeId(), message.getCampaignId());

        TrackingEventType eventType;
        try {
            eventType = TrackingEventType.valueOf(message.getEventType());
        } catch (IllegalArgumentException | NullPointerException e) {
            log.warn("Ignoring unknown tracking event type '{}' for campaign {} / employee {}",
                    message.getEventType(), message.getCampaignId(), message.getEmployeeId());
            return;
        }

        // Idempotency check: prevent duplicate events
        if (trackingEventRepository.existsByCampaignIdAndEmployeeIdAndEventType(
                message.getCampaignId(), message.getEmployeeId(), eventType)) {
            log.warn("Duplicate tracking event ignored: {} for employee {} in campaign {}",
                    eventType, message.getEmployeeId(), message.getCampaignId());
            return;
        }

        LocalDateTime occurredAt = toLocalDateTimeUtc(message.getTimestamp());

        // 1. Save tracking event to PostgreSQL
        TrackingEvent trackingEvent = TrackingEvent.builder()
                .campaignId(message.getCampaignId())
                .employeeId(message.getEmployeeId())
                .companyId(message.getCompanyId())
                .eventType(eventType)
                .occurredAt(occurredAt)
                .build();

        trackingEventRepository.save(trackingEvent);

        // 2. Update employee risk score
        employeeRepository.findById(message.getEmployeeId()).ifPresent(employee -> {
            double increment = switch (eventType) {
                case EMAIL_OPENED -> RISK_INCREMENT_EMAIL_OPENED;
                case LINK_CLICKED -> RISK_INCREMENT_LINK_CLICKED;
                case CREDENTIALS_SUBMITTED -> RISK_INCREMENT_CREDENTIALS;
            };

            // If link was clicked, also mark email as opened (logical inference)
            if ((eventType == TrackingEventType.LINK_CLICKED || eventType == TrackingEventType.CREDENTIALS_SUBMITTED) && !trackingEventRepository.existsByCampaignIdAndEmployeeIdAndEventType(
                        message.getCampaignId(), message.getEmployeeId(), TrackingEventType.EMAIL_OPENED)) {
                    TrackingEvent impliedOpen = TrackingEvent.builder()
                            .campaignId(message.getCampaignId())
                            .employeeId(message.getEmployeeId())
                            .companyId(message.getCompanyId())
                            .eventType(TrackingEventType.EMAIL_OPENED)
                            .occurredAt(occurredAt)
                            .build();
                    trackingEventRepository.save(impliedOpen);
                }
            

            employee.setRiskScore(Math.min(employee.getRiskScore() + increment, 100.0)); // Cap at 100
            employee.setLastPhishedAt(LocalDateTime.now(ZoneOffset.UTC));
            employeeRepository.save(employee);

            log.info("Employee {} risk score updated to {}", employee.getId(), employee.getRiskScore());
        });
    }

    private LocalDateTime toLocalDateTimeUtc(Instant timestamp) {
        if (timestamp == null) {
            return LocalDateTime.now(ZoneOffset.UTC);
        }
        return LocalDateTime.ofInstant(timestamp, ZoneOffset.UTC);
    }
}
