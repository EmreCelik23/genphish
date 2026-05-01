package com.genphish.campaign.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class ApiSecurityFilterTest {

    private static final String API_TOKEN = "test-api-token";
    private static final String JWT_SECRET = "abcdefghijklmnopqrstuvwxyz123456";
    private static final String JWT_ISSUER = "genphish-auth";
    private static final String JWT_AUDIENCE = "genphish-api";

    private ApiSecurityFilter filter;

    @BeforeEach
    void setUp() {
        filter = new ApiSecurityFilter();
        ReflectionTestUtils.setField(filter, "securityEnabled", true);
        ReflectionTestUtils.setField(filter, "apiToken", API_TOKEN);
        ReflectionTestUtils.setField(filter, "companyHeaderName", "X-Company-Id");
        ReflectionTestUtils.setField(filter, "serviceTokenHeaderName", "X-Service-Token");
        ReflectionTestUtils.setField(filter, "companyHeaderAliases", "");
        ReflectionTestUtils.setField(filter, "serviceTokenHeaderAliases", "");
        ReflectionTestUtils.setField(filter, "jwtEnabled", true);
        ReflectionTestUtils.setField(filter, "jwtSecret", JWT_SECRET);
        ReflectionTestUtils.setField(filter, "jwtIssuer", JWT_ISSUER);
        ReflectionTestUtils.setField(filter, "jwtAudience", JWT_AUDIENCE);
        ReflectionTestUtils.setField(filter, "jwtCompanyClaim", "companyId");
        ReflectionTestUtils.setField(filter, "jwtClockSkewSeconds", 30L);
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

    @Test
    void companyScopedApi_ShouldAcceptConfiguredCompanyHeaderAlias() throws ServletException, IOException {
        String companyId = "123e4567-e89b-12d3-a456-426614174000";
        ReflectionTestUtils.setField(filter, "companyHeaderAliases", "X-Tenant-Id");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/companies/" + companyId + "/templates");
        request.addHeader("Authorization", "Bearer " + API_TOKEN);
        request.addHeader("X-Tenant-Id", companyId);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> chainCalled.set(true));

        assertThat(chainCalled).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void companyScopedApi_ShouldAcceptConfiguredServiceTokenHeaderAlias() throws ServletException, IOException {
        String companyId = "123e4567-e89b-12d3-a456-426614174000";
        ReflectionTestUtils.setField(filter, "serviceTokenHeaderAliases", "X-Internal-Token");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/companies/" + companyId + "/templates");
        request.addHeader("X-Internal-Token", API_TOKEN);
        request.addHeader("X-Company-Id", companyId);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> chainCalled.set(true));

        assertThat(chainCalled).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void companyScopedApi_ShouldReturnForbiddenWhenCompanyHeaderMismatchesPath() throws ServletException, IOException {
        String companyId = "123e4567-e89b-12d3-a456-426614174000";
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/companies/" + companyId + "/templates");
        request.addHeader("Authorization", "Bearer " + API_TOKEN);
        request.addHeader("X-Company-Id", "123e4567-e89b-12d3-a456-426614174999");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
        });

        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void optionsPreflight_ShouldBypassAuth() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/companies/123e4567-e89b-12d3-a456-426614174000/templates");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> chainCalled.set(true));

        assertThat(chainCalled).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void referenceImageGet_ShouldBypassWhenPublicAccessEnabled() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/reference-images/template.png");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> chainCalled.set(true));

        assertThat(chainCalled).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void companyScopedApi_ShouldAcceptValidJwtWithoutCompanyHeader() throws Exception {
        String companyId = "123e4567-e89b-12d3-a456-426614174000";
        String token = createJwt(companyId, Instant.now().plusSeconds(300).getEpochSecond());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/companies/" + companyId + "/templates");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> chainCalled.set(true));

        assertThat(chainCalled).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void companyScopedApi_ShouldRejectJwtWhenExpired() throws Exception {
        String companyId = "123e4567-e89b-12d3-a456-426614174000";
        String token = createJwt(companyId, Instant.now().minusSeconds(120).getEpochSecond());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/companies/" + companyId + "/templates");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
        });

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void companyScopedApi_ShouldRejectJwtWhenCompanyClaimDoesNotMatchPath() throws Exception {
        String companyId = "123e4567-e89b-12d3-a456-426614174000";
        String token = createJwt("123e4567-e89b-12d3-a456-426614174999", Instant.now().plusSeconds(300).getEpochSecond());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/companies/" + companyId + "/templates");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
        });

        assertThat(response.getStatus()).isEqualTo(403);
    }

    private String createJwt(String companyId, long expEpochSeconds) throws Exception {
        String headerJson = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
        String payloadJson = String.format(
                "{\"iss\":\"%s\",\"aud\":\"%s\",\"exp\":%d,\"companyId\":\"%s\"}",
                JWT_ISSUER,
                JWT_AUDIENCE,
                expEpochSeconds,
                companyId
        );

        String encodedHeader = base64UrlEncode(headerJson.getBytes(StandardCharsets.UTF_8));
        String encodedPayload = base64UrlEncode(payloadJson.getBytes(StandardCharsets.UTF_8));
        String signingInput = encodedHeader + "." + encodedPayload;
        String signature = base64UrlEncode(hmacSha256(signingInput, JWT_SECRET));
        return signingInput + "." + signature;
    }

    private byte[] hmacSha256(String message, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
    }

    private String base64UrlEncode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
