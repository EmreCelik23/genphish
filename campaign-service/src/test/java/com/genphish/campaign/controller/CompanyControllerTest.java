package com.genphish.campaign.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.genphish.campaign.dto.request.CreateCompanyRequest;
import com.genphish.campaign.dto.response.CompanyResponse;
import com.genphish.campaign.service.CompanyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CompanyControllerTest {

    @Mock
    private CompanyService companyService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        CompanyController controller = new CompanyController(companyService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void createCompany_ShouldReturnCreated() throws Exception {
        UUID companyId = UUID.randomUUID();
        CreateCompanyRequest request = new CreateCompanyRequest();
        request.setName("Acme");
        request.setDomain("acme.com");

        CompanyResponse response = CompanyResponse.builder()
                .id(companyId)
                .name("Acme")
                .domain("acme.com")
                .createdAt(LocalDateTime.now())
                .build();

        when(companyService.createCompany(any(CreateCompanyRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(companyId.toString()))
                .andExpect(jsonPath("$.name").value("Acme"))
                .andExpect(jsonPath("$.domain").value("acme.com"));

        verify(companyService).createCompany(any(CreateCompanyRequest.class));
    }

    @Test
    void getAllCompanies_ShouldReturnList() throws Exception {
        UUID companyId = UUID.randomUUID();
        CompanyResponse response = CompanyResponse.builder()
                .id(companyId)
                .name("Acme")
                .domain("acme.com")
                .createdAt(LocalDateTime.now())
                .build();

        when(companyService.getAllCompanies()).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/companies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(companyId.toString()))
                .andExpect(jsonPath("$[0].name").value("Acme"));
    }

    @Test
    void getCompanyById_ShouldReturnCompany() throws Exception {
        UUID companyId = UUID.randomUUID();
        CompanyResponse response = CompanyResponse.builder()
                .id(companyId)
                .name("Acme")
                .domain("acme.com")
                .createdAt(LocalDateTime.now())
                .build();

        when(companyService.getCompanyById(companyId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/companies/{companyId}", companyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(companyId.toString()))
                .andExpect(jsonPath("$.domain").value("acme.com"));
    }
}
