package com.genphish.campaign.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class EmployeeResponse {
    private UUID id;
    private String firstName;
    private String lastName;
    private String email;
    private String department;
    private Double riskScore;
    private boolean isActive;
    private LocalDateTime lastPhishedAt;
    private LocalDateTime createdAt;
}
