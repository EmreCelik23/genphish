package com.genphish.campaign.dto.request;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CloneCampaignRequest {

    @NotBlank(message = "Cloned campaign name is required")
    private String name;

    @FutureOrPresent(message = "Scheduled time must be in the present or future")
    private LocalDateTime scheduledFor;
}
