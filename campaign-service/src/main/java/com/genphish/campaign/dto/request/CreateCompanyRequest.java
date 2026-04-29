package com.genphish.campaign.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateCompanyRequest {

    @NotBlank(message = "Company name is required")
    private String name;

    @NotBlank(message = "Company domain is required")
    private String domain; // e.g., acme.com
}
