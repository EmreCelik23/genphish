package com.genphish.campaign.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class CompanyResponse {
    private UUID id;
    private String name;
    private String domain;
    private LocalDateTime createdAt;
}
