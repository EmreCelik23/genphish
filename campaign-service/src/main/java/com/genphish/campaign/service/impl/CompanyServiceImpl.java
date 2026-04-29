package com.genphish.campaign.service.impl;

import com.genphish.campaign.dto.request.CreateCompanyRequest;
import com.genphish.campaign.dto.response.CompanyResponse;
import com.genphish.campaign.entity.Company;
import com.genphish.campaign.exception.DuplicateResourceException;
import com.genphish.campaign.exception.ResourceNotFoundException;
import com.genphish.campaign.repository.CompanyRepository;
import com.genphish.campaign.service.CompanyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;


@Service
@RequiredArgsConstructor
@Slf4j
public class CompanyServiceImpl implements CompanyService {

    private final CompanyRepository companyRepository;

    @Override
    @Transactional
    public CompanyResponse createCompany(CreateCompanyRequest request) {
        // Check for duplicate domain (including inactive companies)
        companyRepository.findByDomain(request.getDomain()).ifPresent(existing -> {
            if (existing.isActive()) {
                throw new DuplicateResourceException("Company", "domain", request.getDomain());
            } else {
                throw new DuplicateResourceException("Company", "domain", request.getDomain() + " (Account deactivated, please contact support)");
            }
        });

        Company company = Company.builder()
                .name(request.getName())
                .domain(request.getDomain())
                .build();

        Company saved = companyRepository.save(company);
        log.info("Company created successfully: {} with domain: {}", saved.getId(), saved.getDomain());
        return mapToResponse(saved);
    }

    @Override
    public CompanyResponse getCompanyById(UUID companyId) {
        Company company = companyRepository.findByIdAndIsActive(companyId, true)
                .orElseThrow(() -> new ResourceNotFoundException("Company", "id", companyId));
        return mapToResponse(company);
    }

    @Override
    public List<CompanyResponse> getAllCompanies() {
        return companyRepository.findAllByIsActive(true).stream()
                .map(this::mapToResponse)
                .toList();
    }

    private CompanyResponse mapToResponse(Company company) {
        return CompanyResponse.builder()
                .id(company.getId())
                .name(company.getName())
                .domain(company.getDomain())
                .createdAt(company.getCreatedAt())
                .build();
    }
}
