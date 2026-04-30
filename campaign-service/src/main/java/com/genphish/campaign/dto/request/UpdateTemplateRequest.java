package com.genphish.campaign.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTemplateRequest {

    @NotBlank(message = "Template name is required")
    private String name;

    @NotBlank(message = "Email subject is required")
    private String emailSubject;

    @NotBlank(message = "Email body is required")
    private String emailBody;

    private String landingPageHtml; // Can be empty if ONLY_EMAIL was generated
}
