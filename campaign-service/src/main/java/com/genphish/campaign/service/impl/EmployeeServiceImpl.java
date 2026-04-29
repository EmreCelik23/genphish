package com.genphish.campaign.service.impl;

import com.genphish.campaign.dto.request.CreateEmployeeRequest;
import com.genphish.campaign.dto.request.UpdateEmployeeRequest;
import com.genphish.campaign.dto.response.EmployeeResponse;
import com.genphish.campaign.dto.response.EmployeeRiskProfileResponse;
import com.genphish.campaign.dto.response.ImportResultResponse;
import com.genphish.campaign.entity.Employee;
import com.genphish.campaign.entity.enums.TrackingEventType;
import com.genphish.campaign.exception.DuplicateResourceException;
import com.genphish.campaign.exception.ResourceNotFoundException;
import com.genphish.campaign.repository.EmployeeRepository;
import com.genphish.campaign.repository.TrackingEventRepository;
import com.genphish.campaign.service.EmployeeService;
import com.genphish.campaign.util.FileImportUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;


@Service
@RequiredArgsConstructor
@Slf4j
public class EmployeeServiceImpl implements EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final TrackingEventRepository trackingEventRepository;
    private final FileImportUtil fileImportUtil;

    @Override
    @Transactional
    public ImportResultResponse importEmployees(UUID companyId, MultipartFile file) {
        List<Employee> parsedEmployees = fileImportUtil.parseFile(file, companyId);

        int imported = 0;
        int duplicates = 0;
        int failed = 0;

        for (Employee emp : parsedEmployees) {
            java.util.Optional<Employee> existingOpt = employeeRepository.findByEmailAndCompanyId(emp.getEmail(), companyId);
            
            if (existingOpt.isPresent()) {
                Employee existing = existingOpt.get();
                if (existing.isActive()) {
                    duplicates++;
                } else {
                    // Reactivate previously deleted employee
                    existing.setActive(true);
                    existing.setFirstName(emp.getFirstName());
                    existing.setLastName(emp.getLastName());
                    existing.setDepartment(emp.getDepartment());
                    try {
                        employeeRepository.save(existing);
                        imported++;
                    } catch (RuntimeException e) {
                        failed++;
                        log.warn("Failed to reactivate employee: {} - {}", emp.getEmail(), e.getMessage());
                    }
                }
            } else {
                try {
                    employeeRepository.save(emp);
                    imported++;
                } catch (RuntimeException e) {
                    failed++;
                    log.warn("Failed to import employee: {} - {}", emp.getEmail(), e.getMessage());
                }
            }
        }

        log.info("Import complete for company {}: {} imported, {} duplicates, {} failed",
                companyId, imported, duplicates, failed);

        return ImportResultResponse.builder()
                .totalRows(parsedEmployees.size())
                .imported(imported)
                .duplicates(duplicates)
                .failed(failed)
                .build();
    }

    @Override
    @Transactional
    public EmployeeResponse createEmployee(UUID companyId, CreateEmployeeRequest request) {
        java.util.Optional<Employee> existingOpt = employeeRepository.findByEmailAndCompanyId(request.getEmail(), companyId);

        if (existingOpt.isPresent()) {
            Employee existing = existingOpt.get();
            if (existing.isActive()) {
                throw new DuplicateResourceException("Employee", "email", request.getEmail());
            } else {
                // Reactivate and update
                existing.setActive(true);
                existing.setFirstName(request.getFirstName());
                existing.setLastName(request.getLastName());
                existing.setDepartment(request.getDepartment());
                Employee saved = employeeRepository.save(existing);
                log.info("Reactivated previously deleted employee: {}", saved.getId());
                return mapToResponse(saved);
            }
        }

        Employee employee = Employee.builder()
                .companyId(companyId)
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .department(request.getDepartment())
                .build();

        Employee saved = employeeRepository.save(employee);
        log.info("Employee created successfully: {} in company: {}", saved.getId(), companyId);
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public EmployeeResponse updateEmployee(UUID companyId, UUID employeeId, UpdateEmployeeRequest request) {
        Employee employee = findEmployeeOrThrow(companyId, employeeId);
        employee.setFirstName(request.getFirstName());
        employee.setLastName(request.getLastName());
        employee.setEmail(request.getEmail());
        employee.setDepartment(request.getDepartment());

        Employee updated = employeeRepository.save(employee);
        log.info("Employee updated successfully: {} in company: {}", updated.getId(), companyId);
        return mapToResponse(updated);
    }

    @Override
    public List<EmployeeResponse> getAllEmployees(UUID companyId) {
        return employeeRepository.findAllByCompanyIdAndIsActive(companyId, true).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    public List<EmployeeResponse> getByDepartment(UUID companyId, String department) {
        return employeeRepository.findAllByCompanyIdAndDepartmentAndIsActive(companyId, department, true).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    public List<EmployeeResponse> getHighRiskEmployees(UUID companyId, Double threshold) {
        return employeeRepository.findAllByCompanyIdAndRiskScoreGreaterThanEqualAndIsActive(companyId, threshold, true).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    public EmployeeRiskProfileResponse getRiskProfile(UUID companyId, UUID employeeId) {
        Employee employee = findEmployeeOrThrow(companyId, employeeId);

        long emailsOpened = trackingEventRepository.countByEmployeeIdAndEventType(employeeId, TrackingEventType.EMAIL_OPENED);
        long linksClicked = trackingEventRepository.countByEmployeeIdAndEventType(employeeId, TrackingEventType.LINK_CLICKED);
        long credentialsSubmitted = trackingEventRepository.countByEmployeeIdAndEventType(employeeId, TrackingEventType.CREDENTIALS_SUBMITTED);

        // Total unique campaigns this employee participated in
        long totalCampaigns = trackingEventRepository.findAllByEmployeeId(employeeId).stream()
                .map(e -> e.getCampaignId())
                .distinct()
                .count();

        EmployeeRiskProfileResponse response = EmployeeRiskProfileResponse.builder()
                .employeeId(employee.getId())
                .fullName(employee.getFirstName() + " " + employee.getLastName())
                .email(employee.getEmail())
                .department(employee.getDepartment())
                .riskScore(employee.getRiskScore())
                .totalCampaigns(totalCampaigns)
                .emailsOpened(emailsOpened)
                .linksClicked(linksClicked)
                .credentialsSubmitted(credentialsSubmitted)
                .lastPhishedAt(employee.getLastPhishedAt())
                .build();
                
        log.info("Calculated risk profile for employee: {}. Score: {}", employeeId, employee.getRiskScore());
        return response;
    }

    @Override
    @Transactional
    public void deactivateEmployee(UUID companyId, UUID employeeId) {
        Employee employee = findEmployeeOrThrow(companyId, employeeId);
        employee.setActive(false);
        employeeRepository.save(employee);
        log.info("Employee deactivated: {}", employeeId);
    }

    // ── Private Helpers ──

    private Employee findEmployeeOrThrow(UUID companyId, UUID employeeId) {
        return employeeRepository.findById(employeeId)
                .filter(e -> e.getCompanyId().equals(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("Employee", "id", employeeId));
    }

    private EmployeeResponse mapToResponse(Employee employee) {
        return EmployeeResponse.builder()
                .id(employee.getId())
                .firstName(employee.getFirstName())
                .lastName(employee.getLastName())
                .email(employee.getEmail())
                .department(employee.getDepartment())
                .riskScore(employee.getRiskScore())
                .isActive(employee.isActive())
                .lastPhishedAt(employee.getLastPhishedAt())
                .createdAt(employee.getCreatedAt())
                .build();
    }
}
