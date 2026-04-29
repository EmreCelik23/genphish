package com.genphish.campaign.service;

import com.genphish.campaign.dto.request.CreateEmployeeRequest;
import com.genphish.campaign.dto.request.UpdateEmployeeRequest;
import com.genphish.campaign.dto.response.EmployeeResponse;
import com.genphish.campaign.dto.response.EmployeeRiskProfileResponse;
import com.genphish.campaign.dto.response.ImportResultResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface EmployeeService {

    // CSV/XLSX bulk import
    ImportResultResponse importEmployees(UUID companyId, MultipartFile file);

    // Single employee CRUD
    EmployeeResponse createEmployee(UUID companyId, CreateEmployeeRequest request);

    EmployeeResponse updateEmployee(UUID companyId, UUID employeeId, UpdateEmployeeRequest request);

    // Queries
    List<EmployeeResponse> getAllEmployees(UUID companyId);

    List<EmployeeResponse> getByDepartment(UUID companyId, String department);

    List<EmployeeResponse> getHighRiskEmployees(UUID companyId, Double threshold);

    // Risk profile (individual scorecard)
    EmployeeRiskProfileResponse getRiskProfile(UUID companyId, UUID employeeId);

    // Soft delete (deactivate)
    void deactivateEmployee(UUID companyId, UUID employeeId);
}
