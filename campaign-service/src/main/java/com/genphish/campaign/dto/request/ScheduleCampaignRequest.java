package com.genphish.campaign.dto.request;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ScheduleCampaignRequest {

    @NotNull(message = "Scheduled time is required")
    @FutureOrPresent(message = "Scheduled time must be in the present or future")
    private LocalDateTime scheduledFor;
}
