package com.genphish.campaign.dto.response;

import com.genphish.campaign.entity.enums.DifficultyLevel;
import com.genphish.campaign.entity.enums.LanguageCode;
import com.genphish.campaign.entity.enums.TemplateStatus;
import com.genphish.campaign.entity.enums.TemplateType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PhishingTemplateResponse {
    private UUID id;
    private UUID companyId;
    private String name;
    private String category;
    private TemplateType type;
    private TemplateStatus status;
    private DifficultyLevel difficultyLevel;
    private LanguageCode languageCode;
    private String emailSubject;
    private String emailBody;
    private String landingPageHtml;
    private String prompt;
    private String targetUrl;
    private boolean fallbackContentUsed;
}
