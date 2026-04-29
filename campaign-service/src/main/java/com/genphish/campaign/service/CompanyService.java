package com.genphish.campaign.service;

import com.genphish.campaign.dto.request.CreateCompanyRequest;
import com.genphish.campaign.dto.response.CompanyResponse;

import java.util.List;
import java.util.UUID;

public interface CompanyService {

    CompanyResponse createCompany(CreateCompanyRequest request);

    CompanyResponse getCompanyById(UUID companyId);

    List<CompanyResponse> getAllCompanies();
}
