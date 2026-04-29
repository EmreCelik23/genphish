package com.genphish.campaign.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.genphish.campaign.dto.request.CreateEmployeeRequest;
import com.genphish.campaign.dto.request.UpdateEmployeeRequest;
import com.genphish.campaign.dto.response.EmployeeResponse;
import com.genphish.campaign.dto.response.EmployeeRiskProfileResponse;
import com.genphish.campaign.dto.response.ImportResultResponse;
import com.genphish.campaign.service.EmployeeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class EmployeeControllerTest {

    @Mock
    private EmployeeService employeeService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        EmployeeController controller = new EmployeeController(employeeService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void importEmployees_ShouldReturnResult() throws Exception {
        UUID companyId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("file", "employees.csv", "text/csv", "content".getBytes());

        ImportResultResponse response = ImportResultResponse.builder()
                .totalRows(10)
                .imported(8)
                .duplicates(1)
                .failed(1)
                .build();

        when(employeeService.importEmployees(eq(companyId), any())).thenReturn(response);

        mockMvc.perform(multipart("/api/v1/companies/{companyId}/employees/import", companyId)
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(10))
                .andExpect(jsonPath("$.imported").value(8));
    }

    @Test
    void createEmployee_ShouldReturnCreated() throws Exception {
        UUID companyId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();

        CreateEmployeeRequest request = new CreateEmployeeRequest();
        request.setFirstName("Ali");
        request.setLastName("Yilmaz");
        request.setEmail("ali@example.com");
        request.setDepartment("IT");

        EmployeeResponse response = EmployeeResponse.builder()
                .id(employeeId)
                .firstName("Ali")
                .lastName("Yilmaz")
                .email("ali@example.com")
                .department("IT")
                .riskScore(0.0)
                .isActive(true)
                .build();

        when(employeeService.createEmployee(eq(companyId), any(CreateEmployeeRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/companies/{companyId}/employees", companyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(employeeId.toString()))
                .andExpect(jsonPath("$.email").value("ali@example.com"));
    }

    @Test
    void updateEmployee_ShouldReturnUpdatedEmployee() throws Exception {
        UUID companyId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();

        UpdateEmployeeRequest request = new UpdateEmployeeRequest();
        request.setFirstName("Ayse");
        request.setLastName("Kaya");
        request.setEmail("ayse@example.com");
        request.setDepartment("Finance");

        EmployeeResponse response = EmployeeResponse.builder()
                .id(employeeId)
                .firstName("Ayse")
                .lastName("Kaya")
                .email("ayse@example.com")
                .department("Finance")
                .riskScore(10.0)
                .isActive(true)
                .build();

        when(employeeService.updateEmployee(eq(companyId), eq(employeeId), any(UpdateEmployeeRequest.class)))
                .thenReturn(response);

        mockMvc.perform(put("/api/v1/companies/{companyId}/employees/{employeeId}", companyId, employeeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(employeeId.toString()))
                .andExpect(jsonPath("$.department").value("Finance"));
    }

    @Test
    void getAllEmployees_WithoutFilters_ShouldCallGetAllEmployees() throws Exception {
        UUID companyId = UUID.randomUUID();
        EmployeeResponse employee = EmployeeResponse.builder()
                .id(UUID.randomUUID())
                .firstName("Ali")
                .lastName("Yilmaz")
                .email("ali@example.com")
                .department("IT")
                .riskScore(10.0)
                .isActive(true)
                .build();

        when(employeeService.getAllEmployees(companyId)).thenReturn(List.of(employee));

        mockMvc.perform(get("/api/v1/companies/{companyId}/employees", companyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(employee.getId().toString()));

        verify(employeeService).getAllEmployees(companyId);
        verify(employeeService, never()).getByDepartment(eq(companyId), any());
        verify(employeeService, never()).getHighRiskEmployees(eq(companyId), any());
    }

    @Test
    void getAllEmployees_WithDepartmentFilter_ShouldCallDepartmentService() throws Exception {
        UUID companyId = UUID.randomUUID();
        when(employeeService.getByDepartment(companyId, "HR")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/companies/{companyId}/employees", companyId)
                        .param("department", "HR"))
                .andExpect(status().isOk());

        verify(employeeService).getByDepartment(companyId, "HR");
        verify(employeeService, never()).getAllEmployees(companyId);
    }

    @Test
    void getAllEmployees_WithRiskThresholdFilter_ShouldCallRiskService() throws Exception {
        UUID companyId = UUID.randomUUID();
        when(employeeService.getHighRiskEmployees(companyId, 70.0)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/companies/{companyId}/employees", companyId)
                        .param("riskThreshold", "70"))
                .andExpect(status().isOk());

        verify(employeeService).getHighRiskEmployees(companyId, 70.0);
        verify(employeeService, never()).getAllEmployees(companyId);
    }

    @Test
    void getRiskProfile_ShouldReturnProfile() throws Exception {
        UUID companyId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();

        EmployeeRiskProfileResponse profile = EmployeeRiskProfileResponse.builder()
                .employeeId(employeeId)
                .fullName("Ali Yilmaz")
                .email("ali@example.com")
                .department("IT")
                .riskScore(35.0)
                .totalCampaigns(5)
                .emailsOpened(3)
                .linksClicked(2)
                .credentialsSubmitted(1)
                .build();

        when(employeeService.getRiskProfile(companyId, employeeId)).thenReturn(profile);

        mockMvc.perform(get("/api/v1/companies/{companyId}/employees/{employeeId}/risk-profile", companyId, employeeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value(employeeId.toString()))
                .andExpect(jsonPath("$.riskScore").value(35.0));
    }

    @Test
    void deactivateEmployee_ShouldReturnNoContent() throws Exception {
        UUID companyId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/companies/{companyId}/employees/{employeeId}", companyId, employeeId))
                .andExpect(status().isNoContent());

        verify(employeeService).deactivateEmployee(companyId, employeeId);
    }
}
