package com.genphish.campaign.service.impl;

import com.genphish.campaign.dto.request.CreateEmployeeRequest;
import com.genphish.campaign.dto.request.UpdateEmployeeRequest;
import com.genphish.campaign.dto.response.EmployeeResponse;
import com.genphish.campaign.dto.response.EmployeeRiskProfileResponse;
import com.genphish.campaign.dto.response.ImportResultResponse;
import com.genphish.campaign.entity.Employee;
import com.genphish.campaign.entity.TrackingEvent;
import com.genphish.campaign.entity.enums.TrackingEventType;
import com.genphish.campaign.exception.DuplicateResourceException;
import com.genphish.campaign.repository.EmployeeRepository;
import com.genphish.campaign.repository.TrackingEventRepository;
import com.genphish.campaign.util.FileImportUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceImplTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private TrackingEventRepository trackingEventRepository;

    @Mock
    private FileImportUtil fileImportUtil;

    @InjectMocks
    private EmployeeServiceImpl employeeService;

    private UUID companyId;
    private UUID employeeId;
    private Employee testEmployee;

    @BeforeEach
    void setUp() {
        companyId = UUID.randomUUID();
        employeeId = UUID.randomUUID();
        testEmployee = Employee.builder()
                .id(employeeId)
                .companyId(companyId)
                .firstName("John")
                .lastName("Doe")
                .email("john.doe@test.com")
                .department("IT")
                .isActive(true)
                .riskScore(45.0)
                .build();
    }

    @Test
    void importEmployees_ShouldReturnImportResult() {
        // Given
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", "content".getBytes());
        Employee newEmployee = Employee.builder().email("new@test.com").build();
        Employee duplicateEmployee = Employee.builder().email("john.doe@test.com").build();
        
        when(fileImportUtil.parseFile(file, companyId)).thenReturn(List.of(newEmployee, duplicateEmployee));
        when(employeeRepository.findByEmailAndCompanyId("new@test.com", companyId)).thenReturn(Optional.empty());
        when(employeeRepository.findByEmailAndCompanyId("john.doe@test.com", companyId)).thenReturn(Optional.of(testEmployee));
        
        when(employeeRepository.save(any(Employee.class))).thenReturn(newEmployee);

        // When
        ImportResultResponse result = employeeService.importEmployees(companyId, file);

        // Then
        assertThat(result.getTotalRows()).isEqualTo(2);
        assertThat(result.getImported()).isEqualTo(1);
        assertThat(result.getDuplicates()).isEqualTo(1);
        assertThat(result.getFailed()).isZero();
        
        verify(employeeRepository, times(1)).save(any(Employee.class));
    }

    @Test
    void createEmployee_WhenEmailExistsAndActive_ShouldThrowDuplicateResourceException() {
        // Given
        CreateEmployeeRequest request = new CreateEmployeeRequest();
        request.setEmail("john.doe@test.com");
        
        when(employeeRepository.findByEmailAndCompanyId(request.getEmail(), companyId))
                .thenReturn(Optional.of(testEmployee));

        // When & Then
        assertThatThrownBy(() -> employeeService.createEmployee(companyId, request))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void createEmployee_WhenEmailExistsAndInactive_ShouldReactivate() {
        // Given
        testEmployee.setActive(false);
        CreateEmployeeRequest request = new CreateEmployeeRequest();
        request.setEmail("john.doe@test.com");
        request.setFirstName("John Reactivated");
        
        when(employeeRepository.findByEmailAndCompanyId(request.getEmail(), companyId))
                .thenReturn(Optional.of(testEmployee));
        when(employeeRepository.save(any(Employee.class))).thenReturn(testEmployee);

        // When
        EmployeeResponse response = employeeService.createEmployee(companyId, request);

        // Then
        assertThat(response.isActive()).isTrue();
        assertThat(response.getFirstName()).isEqualTo("John Reactivated");
    }

    @Test
    void updateEmployee_WhenValid_ShouldUpdateAndReturn() {
        // Given
        UpdateEmployeeRequest request = new UpdateEmployeeRequest();
        request.setFirstName("Jane");
        request.setLastName("Doe");
        request.setEmail("jane.doe@test.com");
        request.setDepartment("Finance");
        
        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(testEmployee));
        when(employeeRepository.save(any(Employee.class))).thenReturn(testEmployee);

        // When
        EmployeeResponse response = employeeService.updateEmployee(companyId, employeeId, request);

        // Then
        assertThat(response.getFirstName()).isEqualTo("Jane");
        verify(employeeRepository).save(testEmployee);
    }

    @Test
    void updateEmployee_WhenEmailBelongsToAnotherEmployee_ShouldThrowDuplicateResourceException() {
        // Given
        UpdateEmployeeRequest request = new UpdateEmployeeRequest();
        request.setFirstName("Jane");
        request.setLastName("Doe");
        request.setEmail("existing@test.com");
        request.setDepartment("Finance");

        Employee existingEmployee = Employee.builder()
                .id(UUID.randomUUID())
                .companyId(companyId)
                .email("existing@test.com")
                .isActive(true)
                .build();

        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(testEmployee));
        when(employeeRepository.findByEmailAndCompanyId("existing@test.com", companyId))
                .thenReturn(Optional.of(existingEmployee));

        // When & Then
        assertThatThrownBy(() -> employeeService.updateEmployee(companyId, employeeId, request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Employee already exists with email");

        verify(employeeRepository, never()).save(any(Employee.class));
    }

    @Test
    void getRiskProfile_ShouldCalculateAccurately() {
        // Given
        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(testEmployee));
        
        when(trackingEventRepository.countByEmployeeIdAndEventType(employeeId, TrackingEventType.EMAIL_OPENED)).thenReturn(5L);
        when(trackingEventRepository.countByEmployeeIdAndEventType(employeeId, TrackingEventType.LINK_CLICKED)).thenReturn(3L);
        when(trackingEventRepository.countByEmployeeIdAndEventType(employeeId, TrackingEventType.CREDENTIALS_SUBMITTED)).thenReturn(1L);
        when(trackingEventRepository.countByEmployeeIdAndEventType(employeeId, TrackingEventType.DOWNLOAD_TRIGGERED)).thenReturn(2L);
        when(trackingEventRepository.countByEmployeeIdAndEventType(employeeId, TrackingEventType.CONSENT_GRANTED)).thenReturn(1L);
        
        TrackingEvent event1 = TrackingEvent.builder().campaignId(UUID.randomUUID()).build();
        TrackingEvent event2 = TrackingEvent.builder().campaignId(event1.getCampaignId()).build(); // duplicate campaign
        when(trackingEventRepository.findAllByEmployeeId(employeeId)).thenReturn(List.of(event1, event2));

        // When
        EmployeeRiskProfileResponse profile = employeeService.getRiskProfile(companyId, employeeId);

        // Then
        assertThat(profile.getEmailsOpened()).isEqualTo(5L);
        assertThat(profile.getLinksClicked()).isEqualTo(3L);
        assertThat(profile.getCredentialsSubmitted()).isEqualTo(1L);
        assertThat(profile.getDownloadTriggered()).isEqualTo(2L);
        assertThat(profile.getConsentGranted()).isEqualTo(1L);
        assertThat(profile.getActionsTaken()).isEqualTo(7L);
        assertThat(profile.getTotalCampaigns()).isEqualTo(1L); // distinct campaign count
    }

    @Test
    void deactivateEmployee_ShouldSetActiveToFalse() {
        // Given
        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(testEmployee));

        // When
        employeeService.deactivateEmployee(companyId, employeeId);

        // Then
        assertThat(testEmployee.isActive()).isFalse();
        verify(employeeRepository).save(testEmployee);
    }
}
