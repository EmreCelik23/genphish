package com.genphish.campaign.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@Slf4j
public class ApiSecurityFilter extends OncePerRequestFilter {

    private static final Pattern COMPANY_ID_PATTERN = Pattern.compile(
            "(?i)^/api/v\\d+/companies/([a-f0-9\\-]{36})(?:/.*)?$"
    );

    @Value("${app.security.enabled:true}")
    private boolean securityEnabled;

    @Value("${app.security.api-token:genphish-dev-token}")
    private String apiToken;

    @Value("${app.security.company-header:X-Company-Id}")
    private String companyHeaderName;

    @Value("${app.security.service-token-header:X-Service-Token}")
    private String serviceTokenHeaderName;

    @Value("${app.security.reference-images-public:true}")
    private boolean referenceImagesPublic;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!securityEnabled || isPreflight(request) || isPublicEndpoint(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (isApiRequest(request) && !hasValidToken(request)) {
            reject(response, HttpStatus.UNAUTHORIZED, "Unauthorized request.");
            return;
        }

        if (isCompanyScopedApi(request) && !hasMatchingCompanyHeader(request)) {
            reject(response, HttpStatus.FORBIDDEN, "Company scope header is missing or does not match path.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isApiRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri != null && uri.startsWith("/api/");
    }

    private boolean isCompanyScopedApi(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri != null && COMPANY_ID_PATTERN.matcher(uri).matches();
    }

    private boolean isPublicEndpoint(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return false;
        }

        if (uri.startsWith("/actuator")) {
            return true;
        }

        if (referenceImagesPublic
                && "GET".equalsIgnoreCase(request.getMethod())
                && uri.startsWith("/api/v1/reference-images/")) {
            return true;
        }

        return "/healthz".equalsIgnoreCase(uri);
    }

    private boolean isPreflight(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    private boolean hasValidToken(HttpServletRequest request) {
        String configured = normalize(apiToken);
        if (configured.isEmpty()) {
            log.error("API token is empty while security is enabled.");
            return false;
        }

        String bearerToken = extractBearerToken(request.getHeader(HttpHeaders.AUTHORIZATION));
        if (!bearerToken.isEmpty() && constantTimeEquals(configured, bearerToken)) {
            return true;
        }

        String serviceToken = normalize(request.getHeader(serviceTokenHeaderName));
        return !serviceToken.isEmpty() && constantTimeEquals(configured, serviceToken);
    }

    private boolean hasMatchingCompanyHeader(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return false;
        }

        Matcher matcher = COMPANY_ID_PATTERN.matcher(uri);
        if (!matcher.matches()) {
            return true;
        }

        String expectedCompanyId = normalize(matcher.group(1));
        String receivedCompanyId = normalize(request.getHeader(companyHeaderName));
        return !receivedCompanyId.isEmpty() && expectedCompanyId.equalsIgnoreCase(receivedCompanyId);
    }

    private String extractBearerToken(String authorizationHeader) {
        String header = normalize(authorizationHeader);
        if (header.regionMatches(true, 0, "Bearer ", 0, 7) && header.length() > 7) {
            return normalize(header.substring(7));
        }
        return "";
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean constantTimeEquals(String a, String b) {
        byte[] first = a.getBytes(StandardCharsets.UTF_8);
        byte[] second = b.getBytes(StandardCharsets.UTF_8);
        if (first.length != second.length) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < first.length; i++) {
            diff |= first[i] ^ second[i];
        }
        return diff == 0;
    }

    private void reject(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
