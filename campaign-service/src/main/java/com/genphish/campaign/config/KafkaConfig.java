package com.genphish.campaign.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    // Java → Python: Requests AI to generate phishing email + landing page
    public static final String TOPIC_AI_GENERATION_REQUESTS = "ai_generation_requests";

    // Python → Java: Returns generated content with mongoTemplateId
    public static final String TOPIC_AI_GENERATION_RESPONSES = "ai_generation_responses";

    // Java → Go: Delivers emails via SMTP
    public static final String TOPIC_EMAIL_DELIVERY_QUEUE = "email_delivery_queue";

    // Go → Java: Tracking events (email opened, link clicked, credentials submitted)
    public static final String TOPIC_TRACKING_EVENTS = "tracking_events";

    // Global: Cancellation events
    public static final String TOPIC_CAMPAIGN_CANCELED = "campaign_canceled_events";

    @Bean
    public NewTopic aiGenerationRequestsTopic() {
        return TopicBuilder.name(TOPIC_AI_GENERATION_REQUESTS)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic aiGenerationResponsesTopic() {
        return TopicBuilder.name(TOPIC_AI_GENERATION_RESPONSES)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic emailDeliveryQueueTopic() {
        return TopicBuilder.name(TOPIC_EMAIL_DELIVERY_QUEUE)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic trackingEventsTopic() {
        return TopicBuilder.name(TOPIC_TRACKING_EVENTS)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
