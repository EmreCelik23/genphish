package com.genphish.campaign.controller;

import com.genphish.campaign.dto.request.CreateEmployeeRequest;
import com.genphish.campaign.dto.request.UpdateEmployeeRequest;
import com.genphish.campaign.dto.response.EmployeeResponse;
import com.genphish.campaign.dto.response.EmployeeRiskProfileResponse;
import com.genphish.campaign.dto.response.ImportResultResponse;
import com.genphish.campaign.service.EmployeeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/companies/{companyId}/employees")
@RequiredArgsConstructor
@Slf4j
public class EmployeeController {

    private final EmployeeService employeeService;

    // POST /api/v1/companies/{companyId}/employees/import — CSV/XLSX upload
    @PostMapping("/import")
    public ResponseEntity<ImportResultResponse> importEmployees(
            @PathVariable UUID companyId,
            @RequestParam("file") MultipartFile file) {
        log.info("Received request to import employees for company: {}", companyId);
        ImportResultResponse result = employeeService.importEmployees(companyId, file);
        log.info("Successfully processed employee import for company: {}", companyId);
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    // POST /api/v1/companies/{companyId}/employees — Single employee
    @PostMapping
    public ResponseEntity<EmployeeResponse> createEmployee(
            @PathVariable UUID companyId,
            @Valid @RequestBody CreateEmployeeRequest request) {
        log.info("Received request to create employee for company: {}", companyId);
        EmployeeResponse response = employeeService.createEmployee(companyId, request);
        log.info("Successfully created employee: {}", response.getId());
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PutMapping("/{employeeId}")
    public ResponseEntity<EmployeeResponse> updateEmployee(
            @PathVariable UUID companyId,
            @PathVariable UUID employeeId,
            @Valid @RequestBody UpdateEmployeeRequest request) {
        log.info("Received request to update employee: {} for company: {}", employeeId, companyId);
        EmployeeResponse response = employeeService.updateEmployee(companyId, employeeId, request);
        log.info("Successfully updated employee: {}", employeeId);
        return ResponseEntity.ok(response);
    }

    // GET /api/v1/companies/{companyId}/employees
    @GetMapping
    public ResponseEntity<List<EmployeeResponse>> getAllEmployees(
            @PathVariable UUID companyId,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) Double riskThreshold) {

        log.info("Fetching employees for company: {}, department: {}, riskThreshold: {}", companyId, department, riskThreshold);
        if (department != null) {
            return ResponseEntity.ok(employeeService.getByDepartment(companyId, department));
        }
        if (riskThreshold != null) {
            return ResponseEntity.ok(employeeService.getHighRiskEmployees(companyId, riskThreshold));
        }
        return ResponseEntity.ok(employeeService.getAllEmployees(companyId));
    }

    // GET /api/v1/companies/{companyId}/employees/{employeeId}/risk-profile
    @GetMapping("/{employeeId}/risk-profile")
    public ResponseEntity<EmployeeRiskProfileResponse> getRiskProfile(
            @PathVariable UUID companyId,
            @PathVariable UUID employeeId) {
        log.info("Fetching risk profile for employee: {} in company: {}", employeeId, companyId);
        return ResponseEntity.ok(employeeService.getRiskProfile(companyId, employeeId));
    }

    @DeleteMapping("/{employeeId}")
    public ResponseEntity<Void> deactivateEmployee(
            @PathVariable UUID companyId,
            @PathVariable UUID employeeId) {
        log.info("Received request to deactivate employee: {} in company: {}", employeeId, companyId);
        employeeService.deactivateEmployee(companyId, employeeId);
        log.info("Successfully deactivated employee: {}", employeeId);
        return ResponseEntity.noContent().build();
    }
}
