package com.genphish.campaign.repository;

import com.genphish.campaign.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CompanyRepository extends JpaRepository<Company, UUID> {
    // Finds a company by its domain (e.g., to check if acme.com is already registered)
    Optional<Company> findByDomain(String domain);

    // Retrieves all active companies
    List<Company> findAllByIsActive(boolean isActive);

    // Retrieves an active company by id
    Optional<Company> findByIdAndIsActive(UUID id, boolean isActive);
}