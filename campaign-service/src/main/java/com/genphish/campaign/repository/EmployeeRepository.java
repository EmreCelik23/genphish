package com.genphish.campaign.repository;

import com.genphish.campaign.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, UUID> {

    // Retrieves all active employees for a specific company
    List<Employee> findAllByCompanyIdAndIsActive(UUID companyId, boolean isActive);

    // Retrieves all employees for a specific company (including inactive)
    List<Employee> findAllByCompanyId(UUID companyId);

    // Retrieves employees of a specific department within a company (targeted phishing)
    List<Employee> findAllByCompanyIdAndDepartmentAndIsActive(UUID companyId, String department, boolean isActive);

    // Finds high-risk employees above a threshold (risk-based targeting)
    List<Employee> findAllByCompanyIdAndRiskScoreGreaterThanEqualAndIsActive(UUID companyId, Double riskScore, boolean isActive);

    // Checks if an email already exists in that specific company
    boolean existsByEmailAndCompanyId(String email, UUID companyId);

    // Retrieves an employee by email and company (used for reactivation logic)
    Optional<Employee> findByEmailAndCompanyId(String email, UUID companyId);

    // Count employees per company (for dashboard)
    long countByCompanyIdAndIsActive(UUID companyId, boolean isActive);

    // Count active employees per department
    long countByCompanyIdAndDepartmentAndIsActive(UUID companyId, String department, boolean isActive);
}