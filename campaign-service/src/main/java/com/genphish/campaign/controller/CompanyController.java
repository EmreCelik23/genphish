package com.genphish.campaign.controller;

import com.genphish.campaign.dto.request.CreateCompanyRequest;
import com.genphish.campaign.dto.response.CompanyResponse;
import com.genphish.campaign.service.CompanyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/companies")
@RequiredArgsConstructor
@Slf4j
public class CompanyController {

    private final CompanyService companyService;

    // POST /api/v1/companies
    @PostMapping
    public ResponseEntity<CompanyResponse> createCompany(@Valid @RequestBody CreateCompanyRequest request) {
        log.info("Received request to create company with domain: {}", request.getDomain());
        CompanyResponse response = companyService.createCompany(request);
        log.info("Successfully created company: {}", response.getId());
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    // GET /api/v1/companies
    @GetMapping
    public ResponseEntity<List<CompanyResponse>> getAllCompanies() {
        log.info("Fetching all active companies");
        return ResponseEntity.ok(companyService.getAllCompanies());
    }

    @GetMapping("/{companyId}")
    public ResponseEntity<CompanyResponse> getCompanyById(@PathVariable UUID companyId) {
        log.info("Fetching company by ID: {}", companyId);
        return ResponseEntity.ok(companyService.getCompanyById(companyId));
    }
}
