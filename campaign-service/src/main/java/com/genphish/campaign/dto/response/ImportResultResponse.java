package com.genphish.campaign.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ImportResultResponse {
    private int totalRows;      // Total rows found in file
    private int imported;       // Successfully imported
    private int duplicates;     // Skipped (email already exists in company)
    private int failed;         // Rows with invalid data
}
