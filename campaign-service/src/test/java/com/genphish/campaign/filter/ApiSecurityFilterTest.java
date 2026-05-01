package com.genphish.campaign.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class ApiSecurityFilterTest {

    private static final String API_TOKEN = "test-api-token";

    private ApiSecurityFilter filter;

    @BeforeEach
    void setUp() {
        filter = new ApiSecurityFilter();
        ReflectionTestUtils.setField(filter, "securityEnabled", true);
        ReflectionTestUtils.setField(filter, "apiToken", API_TOKEN);
        ReflectionTestUtils.setField(filter, "companyHeaderName", "X-Company-Id");
        ReflectionTestUtils.setField(filter, "serviceTokenHeaderName", "X-Service-Token");
        ReflectionTestUtils.setField(filter, "referenceImagesPublic", true);
    }

    @Test
    void actuatorEndpoint_ShouldBypassAuth() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> chainCalled.set(true);

        filter.doFilter(request, response, chain);

        assertThat(chainCalled).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void companyScopedApi_ShouldReturnUnauthorizedWhenTokenMissing() throws ServletException, IOException {
        String companyId = "123e4567-e89b-12d3-a456-426614174000";
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/companies/" + companyId + "/templates");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
        });

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void companyScopedApi_ShouldReturnForbiddenWhenCompanyHeaderMissing() throws ServletException, IOException {
        String companyId = "123e4567-e89b-12d3-a456-426614174000";
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/companies/" + companyId + "/templates");
        request.addHeader("Authorization", "Bearer " + API_TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
        });

        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void companyScopedApi_ShouldPassWhenBearerAndCompanyHeaderMatch() throws ServletException, IOException {
        String companyId = "123e4567-e89b-12d3-a456-426614174000";
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/companies/" + companyId + "/templates");
        request.addHeader("Authorization", "Bearer " + API_TOKEN);
        request.addHeader("X-Company-Id", companyId);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> chainCalled.set(true));

        assertThat(chainCalled).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void companyScopedApi_ShouldAcceptServiceTokenHeader() throws ServletException, IOException {
        String companyId = "123e4567-e89b-12d3-a456-426614174000";
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/companies/" + companyId + "/templates/generate");
        request.addHeader("X-Service-Token", API_TOKEN);
        request.addHeader("X-Company-Id", companyId);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> chainCalled.set(true));

        assertThat(chainCalled).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
