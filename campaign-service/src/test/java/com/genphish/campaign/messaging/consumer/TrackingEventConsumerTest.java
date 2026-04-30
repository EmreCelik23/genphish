package com.genphish.campaign.messaging.consumer;

import com.genphish.campaign.entity.Employee;
import com.genphish.campaign.entity.TrackingEvent;
import com.genphish.campaign.entity.enums.TrackingEventType;
import com.genphish.campaign.messaging.event.TrackingEventMessage;
import com.genphish.campaign.repository.EmployeeRepository;
import com.genphish.campaign.repository.TrackingEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrackingEventConsumerTest {

    @Mock
    private TrackingEventRepository trackingEventRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @InjectMocks
    private TrackingEventConsumer consumer;

    @Test
    void consume_WhenDuplicateEvent_ShouldIgnore() {
        UUID campaignId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();

        TrackingEventMessage message = TrackingEventMessage.builder()
                .campaignId(campaignId)
                .companyId(companyId)
                .employeeId(employeeId)
                .eventType("EMAIL_OPENED")
                .timestamp(Instant.now())
                .build();

        when(trackingEventRepository.existsByCampaignIdAndEmployeeIdAndEventType(
                campaignId, employeeId, TrackingEventType.EMAIL_OPENED)
        ).thenReturn(true);

        consumer.consume(message);

        verify(trackingEventRepository, never()).save(org.mockito.ArgumentMatchers.any(TrackingEvent.class));
        verify(employeeRepository, never()).findById(employeeId);
    }

    @Test
    void consume_WhenEventTypeInvalid_ShouldIgnoreGracefully() {
        TrackingEventMessage message = TrackingEventMessage.builder()
                .campaignId(UUID.randomUUID())
                .companyId(UUID.randomUUID())
                .employeeId(UUID.randomUUID())
                .eventType("NOT_A_REAL_EVENT")
                .timestamp(Instant.now())
                .build();

        consumer.consume(message);

        verify(trackingEventRepository, never()).save(org.mockito.ArgumentMatchers.any(TrackingEvent.class));
        verify(employeeRepository, never()).findById(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void consume_WhenEmailOpened_ShouldPersistEventAndIncreaseRisk() {
        UUID campaignId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();

        Employee employee = Employee.builder()
                .id(employeeId)
                .companyId(companyId)
                .riskScore(20.0)
                .build();

        TrackingEventMessage message = TrackingEventMessage.builder()
                .campaignId(campaignId)
                .companyId(companyId)
                .employeeId(employeeId)
                .eventType("EMAIL_OPENED")
                .timestamp(Instant.now())
                .build();

        when(trackingEventRepository.existsByCampaignIdAndEmployeeIdAndEventType(
                campaignId, employeeId, TrackingEventType.EMAIL_OPENED)
        ).thenReturn(false);
        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));

        consumer.consume(message);

        ArgumentCaptor<TrackingEvent> eventCaptor = ArgumentCaptor.forClass(TrackingEvent.class);
        verify(trackingEventRepository).save(eventCaptor.capture());

        TrackingEvent savedEvent = eventCaptor.getValue();
        assertThat(savedEvent.getEventType()).isEqualTo(TrackingEventType.EMAIL_OPENED);
        assertThat(employee.getRiskScore()).isEqualTo(25.0);
        assertThat(employee.getLastPhishedAt()).isNotNull();
        verify(employeeRepository).save(employee);
    }

    @Test
    void consume_WhenLinkClickedAndOpenMissing_ShouldStoreImpliedOpenAndCapRisk() {
        UUID campaignId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();

        Employee employee = Employee.builder()
                .id(employeeId)
                .companyId(companyId)
                .riskScore(95.0)
                .build();

        TrackingEventMessage message = TrackingEventMessage.builder()
                .campaignId(campaignId)
                .companyId(companyId)
                .employeeId(employeeId)
                .eventType("LINK_CLICKED")
                .timestamp(Instant.now())
                .build();

        when(trackingEventRepository.existsByCampaignIdAndEmployeeIdAndEventType(
                campaignId, employeeId, TrackingEventType.LINK_CLICKED)
        ).thenReturn(false);
        when(trackingEventRepository.existsByCampaignIdAndEmployeeIdAndEventType(
                campaignId, employeeId, TrackingEventType.EMAIL_OPENED)
        ).thenReturn(false);
        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));

        consumer.consume(message);

        ArgumentCaptor<TrackingEvent> captor = ArgumentCaptor.forClass(TrackingEvent.class);
        verify(trackingEventRepository, times(2)).save(captor.capture());

        List<TrackingEventType> savedTypes = captor.getAllValues().stream()
                .map(TrackingEvent::getEventType)
                .toList();

        assertThat(savedTypes).containsExactlyInAnyOrder(TrackingEventType.LINK_CLICKED, TrackingEventType.EMAIL_OPENED);
        assertThat(employee.getRiskScore()).isEqualTo(100.0);
        verify(employeeRepository).save(employee);
    }
}
