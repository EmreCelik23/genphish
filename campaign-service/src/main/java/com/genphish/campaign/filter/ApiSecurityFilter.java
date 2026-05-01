package com.genphish.campaign.filter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@Slf4j
public class ApiSecurityFilter extends OncePerRequestFilter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String JWT_COMPANY_ID_ATTR = "genphish.jwt.companyId";
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

    @Value("${app.security.service-token-header-aliases:}")
    private String serviceTokenHeaderAliases;

    @Value("${app.security.company-header-aliases:}")
    private String companyHeaderAliases;

    @Value("${app.security.jwt-enabled:false}")
    private boolean jwtEnabled;

    @Value("${app.security.jwt-secret:}")
    private String jwtSecret;

    @Value("${app.security.jwt-issuer:}")
    private String jwtIssuer;

    @Value("${app.security.jwt-audience:}")
    private String jwtAudience;

    @Value("${app.security.jwt-company-claim:companyId}")
    private String jwtCompanyClaim;

    @Value("${app.security.jwt-clock-skew-seconds:30}")
    private long jwtClockSkewSeconds;

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
        if (!bearerToken.isEmpty()) {
            JwtValidationResult jwtResult = validateJwtIfEnabled(bearerToken);
            if (jwtResult.valid()) {
                if (!jwtResult.companyId().isEmpty()) {
                    request.setAttribute(JWT_COMPANY_ID_ATTR, jwtResult.companyId());
                }
                return true;
            }
            if (constantTimeEquals(configured, bearerToken)) {
                return true;
            }
        }

        String serviceToken = normalize(firstAvailableHeader(request, headerNames(serviceTokenHeaderName, serviceTokenHeaderAliases)));
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
        String receivedCompanyId = normalize(firstAvailableHeader(request, headerNames(companyHeaderName, companyHeaderAliases)));
        if (!receivedCompanyId.isEmpty()) {
            return expectedCompanyId.equalsIgnoreCase(receivedCompanyId);
        }

        String jwtCompanyId = normalize(asString(request.getAttribute(JWT_COMPANY_ID_ATTR)));
        return !jwtCompanyId.isEmpty() && expectedCompanyId.equalsIgnoreCase(jwtCompanyId);
    }

    private JwtValidationResult validateJwtIfEnabled(String token) {
        if (!jwtEnabled) {
            return JwtValidationResult.invalid();
        }
        if (normalize(jwtSecret).isEmpty()) {
            log.error("JWT auth is enabled but app.security.jwt-secret is empty.");
            return JwtValidationResult.invalid();
        }
        try {
            return validateJwt(token);
        } catch (Exception e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            return JwtValidationResult.invalid();
        }
    }

    private JwtValidationResult validateJwt(String token) throws Exception {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return JwtValidationResult.invalid();
        }

        Map<String, Object> header = parseJsonObject(base64UrlDecode(parts[0]));
        String algorithm = normalize(asString(header.get("alg")));
        if (!"HS256".equalsIgnoreCase(algorithm)) {
            return JwtValidationResult.invalid();
        }

        String signingInput = parts[0] + "." + parts[1];
        byte[] expectedSignature = hmacSha256(signingInput, jwtSecret);
        byte[] receivedSignature = base64UrlDecode(parts[2]);
        if (!MessageDigest.isEqual(expectedSignature, receivedSignature)) {
            return JwtValidationResult.invalid();
        }

        Map<String, Object> payload = parseJsonObject(base64UrlDecode(parts[1]));
        long now = Instant.now().getEpochSecond();
        long skew = Math.max(jwtClockSkewSeconds, 0L);

        Long exp = parseLongClaim(payload.get("exp"));
        if (exp == null || now > exp + skew) {
            return JwtValidationResult.invalid();
        }

        Long nbf = parseLongClaim(payload.get("nbf"));
        if (nbf != null && now + skew < nbf) {
            return JwtValidationResult.invalid();
        }

        String expectedIssuer = normalize(jwtIssuer);
        if (!expectedIssuer.isEmpty()) {
            String issuer = normalize(asString(payload.get("iss")));
            if (!expectedIssuer.equals(issuer)) {
                return JwtValidationResult.invalid();
            }
        }

        String expectedAudience = normalize(jwtAudience);
        if (!expectedAudience.isEmpty() && !matchesAudience(payload.get("aud"), expectedAudience)) {
            return JwtValidationResult.invalid();
        }

        String companyClaimName = normalize(jwtCompanyClaim);
        String companyId = companyClaimName.isEmpty()
                ? ""
                : normalize(asString(payload.get(companyClaimName)));
        return JwtValidationResult.valid(companyId);
    }

    private Map<String, Object> parseJsonObject(byte[] bytes) throws IOException {
        return OBJECT_MAPPER.readValue(bytes, new TypeReference<>() {
        });
    }

    private byte[] hmacSha256(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
    }

    private byte[] base64UrlDecode(String value) {
        String normalized = normalize(value);
        int missingPadding = normalized.length() % 4;
        if (missingPadding != 0) {
            normalized += "=".repeat(4 - missingPadding);
        }
        return Base64.getUrlDecoder().decode(normalized);
    }

    private boolean matchesAudience(Object claimValue, String expectedAudience) {
        if (claimValue == null) {
            return false;
        }
        if (claimValue instanceof String audience) {
            return expectedAudience.equals(audience);
        }
        if (claimValue instanceof Collection<?> audiences) {
            return audiences.stream()
                    .map(this::asString)
                    .map(this::normalize)
                    .anyMatch(expectedAudience::equals);
        }
        return false;
    }

    private Long parseLongClaim(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String normalized = normalize(asString(value));
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(normalized);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private List<String> headerNames(String primary, String aliasesCsv) {
        String normalizedPrimary = normalize(primary);
        List<String> aliases = Arrays.stream(normalize(aliasesCsv).split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();

        if (normalizedPrimary.isEmpty()) {
            return aliases;
        }
        return java.util.stream.Stream.concat(
                java.util.stream.Stream.of(normalizedPrimary),
                aliases.stream()
        ).distinct().toList();
    }

    private String firstAvailableHeader(HttpServletRequest request, List<String> names) {
        for (String name : names) {
            String value = request.getHeader(name);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
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

    private record JwtValidationResult(boolean valid, String companyId) {
        private static JwtValidationResult valid(String companyId) {
            return new JwtValidationResult(true, companyId == null ? "" : companyId);
        }

        private static JwtValidationResult invalid() {
            return new JwtValidationResult(false, "");
        }
    }
}
