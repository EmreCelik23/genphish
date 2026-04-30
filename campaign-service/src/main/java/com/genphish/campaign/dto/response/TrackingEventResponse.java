package com.genphish.campaign.dto.response;

import com.genphish.campaign.entity.enums.TrackingEventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackingEventResponse {
    private UUID eventId;
    private UUID employeeId;
    private String employeeName;
    private String employeeDepartment;
    private TrackingEventType eventType;
    private LocalDateTime occurredAt;
}
