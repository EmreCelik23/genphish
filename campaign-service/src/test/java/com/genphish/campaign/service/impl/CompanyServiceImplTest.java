package com.genphish.campaign.service.impl;

import com.genphish.campaign.dto.request.CreateCompanyRequest;
import com.genphish.campaign.dto.response.CompanyResponse;
import com.genphish.campaign.entity.Company;
import com.genphish.campaign.exception.DuplicateResourceException;
import com.genphish.campaign.exception.ResourceNotFoundException;
import com.genphish.campaign.repository.CompanyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompanyServiceImplTest {

    @Mock
    private CompanyRepository companyRepository;

    @InjectMocks
    private CompanyServiceImpl companyService;

    private Company testCompany;
    private UUID companyId;

    @BeforeEach
    void setUp() {
        companyId = UUID.randomUUID();
        testCompany = Company.builder()
                .id(companyId)
                .name("Test Company")
                .domain("test.com")
                .isActive(true)
                .build();
    }

    @Test
    void createCompany_WhenValid_ShouldReturnResponse() {
        // Given
        CreateCompanyRequest request = new CreateCompanyRequest();
        request.setName("Test Company");
        request.setDomain("test.com");

        when(companyRepository.findByName(request.getName())).thenReturn(Optional.empty());
        when(companyRepository.findByDomain(request.getDomain())).thenReturn(Optional.empty());
        when(companyRepository.save(any(Company.class))).thenReturn(testCompany);

        // When
        CompanyResponse response = companyService.createCompany(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(companyId);
        assertThat(response.getName()).isEqualTo("Test Company");
        assertThat(response.getDomain()).isEqualTo("test.com");
        
        verify(companyRepository).findByName("Test Company");
        verify(companyRepository).findByDomain("test.com");
        verify(companyRepository).save(any(Company.class));
    }

    @Test
    void createCompany_WhenNameExistsAndActive_ShouldThrowDuplicateResourceException() {
        // Given
        CreateCompanyRequest request = new CreateCompanyRequest();
        request.setName("Test Company");
        request.setDomain("another.com");

        testCompany.setActive(true);
        when(companyRepository.findByName("Test Company")).thenReturn(Optional.of(testCompany));

        // When & Then
        assertThatThrownBy(() -> companyService.createCompany(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Company already exists with name: 'Test Company'");

        verify(companyRepository, never()).save(any(Company.class));
    }

    @Test
    void createCompany_WhenDomainExistsAndActive_ShouldThrowDuplicateResourceException() {
        // Given
        CreateCompanyRequest request = new CreateCompanyRequest();
        request.setName("New Name");
        request.setDomain("test.com");

        when(companyRepository.findByName("New Name")).thenReturn(Optional.empty());
        testCompany.setActive(true);
        when(companyRepository.findByDomain("test.com")).thenReturn(Optional.of(testCompany));

        // When & Then
        assertThatThrownBy(() -> companyService.createCompany(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Company already exists with domain: 'test.com'");

        verify(companyRepository, never()).save(any(Company.class));
    }

    @Test
    void getCompanyById_WhenExists_ShouldReturnResponse() {
        // Given
        when(companyRepository.findByIdAndIsActive(companyId, true)).thenReturn(Optional.of(testCompany));

        // When
        CompanyResponse response = companyService.getCompanyById(companyId);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(companyId);
        verify(companyRepository).findByIdAndIsActive(companyId, true);
    }

    @Test
    void getCompanyById_WhenNotExists_ShouldThrowResourceNotFoundException() {
        // Given
        when(companyRepository.findByIdAndIsActive(companyId, true)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> companyService.getCompanyById(companyId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Company not found with id");
    }

    @Test
    void getAllCompanies_ShouldReturnList() {
        // Given
        when(companyRepository.findAllByIsActive(true)).thenReturn(List.of(testCompany));

        // When
        List<CompanyResponse> responses = companyService.getAllCompanies();

        // Then
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getId()).isEqualTo(companyId);
    }
}
