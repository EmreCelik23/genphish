package com.genphish.campaign.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
@Slf4j
public class ProductionSecurityValidator implements ApplicationRunner {

    private static final Set<String> FORBIDDEN_DEFAULT_SECRETS = Set.of(
            "genphish-dev-token",
            "genphish-internal-token",
            "genphish-dev-tracking-secret"
    );

    @Value("${app.env:local}")
    private String appEnv;

    @Value("${app.security.enabled:true}")
    private boolean securityEnabled;

    @Value("${app.security.fail-on-unsafe-defaults:true}")
    private boolean failOnUnsafeDefaults;

    @Value("${app.security.require-s3-in-prod:true}")
    private boolean requireS3InProd;

    @Value("${app.security.api-token:genphish-dev-token}")
    private String apiToken;

    @Value("${app.security.ai-service-token:genphish-internal-token}")
    private String aiServiceToken;

    @Value("${app.security.tracking-signature-secret:genphish-dev-tracking-secret}")
    private String trackingSignatureSecret;

    @Value("${app.security.oauth-state-secret:genphish-dev-tracking-secret}")
    private String oauthStateSecret;

    @Value("${app.security.jwt-enabled:false}")
    private boolean jwtEnabled;

    @Value("${app.security.jwt-secret:}")
    private String jwtSecret;

    @Value("${app.security.jwt-issuer:}")
    private String jwtIssuer;

    @Value("${app.security.jwt-audience:}")
    private String jwtAudience;

    @Value("${app.upload.reference-storage:local}")
    private String referenceStorage;

    @Value("${app.upload.s3.bucket:}")
    private String s3Bucket;

    @Override
    public void run(ApplicationArguments args) {
        if (!isProdEnvironment() || !failOnUnsafeDefaults) {
            return;
        }

        List<String> failures = new ArrayList<>();
        if (!securityEnabled) {
            failures.add("app.security.enabled must be true in prod.");
        }

        validateSecret("APP_API_TOKEN", apiToken, failures);
        validateSecret("AI_SERVICE_TOKEN", aiServiceToken, failures);
        validateSecret("TRACKING_SIGNATURE_SECRET", trackingSignatureSecret, failures);
        validateSecret("OAUTH_STATE_HMAC_SECRET", oauthStateSecret, failures);
        if (jwtEnabled) {
            validateSecret("JWT_AUTH_SECRET", jwtSecret, failures);
            if (normalize(jwtIssuer).isEmpty()) {
                failures.add("JWT_AUTH_ISSUER cannot be blank when JWT auth is enabled.");
            }
            if (normalize(jwtAudience).isEmpty()) {
                failures.add("JWT_AUTH_AUDIENCE cannot be blank when JWT auth is enabled.");
            }
        }

        String normalizedStorage = normalize(referenceStorage).toLowerCase(Locale.ROOT);
        if (requireS3InProd && !"s3".equals(normalizedStorage)) {
            failures.add("REFERENCE_IMAGE_STORAGE must be 's3' in prod.");
        }
        if ("s3".equals(normalizedStorage) && normalize(s3Bucket).isEmpty()) {
            failures.add("REFERENCE_IMAGE_S3_BUCKET cannot be empty when REFERENCE_IMAGE_STORAGE=s3.");
        }

        if (!failures.isEmpty()) {
            throw new IllegalStateException("Production security validation failed: " + String.join(" ", failures));
        }

        log.info("Production security validation completed successfully.");
    }

    private void validateSecret(String key, String value, List<String> failures) {
        String normalized = normalize(value);
        if (normalized.isEmpty()) {
            failures.add(key + " cannot be blank.");
            return;
        }
        if (FORBIDDEN_DEFAULT_SECRETS.contains(normalized)) {
            failures.add(key + " cannot use default development value.");
        }
        if (normalized.length() < 32) {
            failures.add(key + " must be at least 32 characters in prod.");
        }
    }

    private boolean isProdEnvironment() {
        return "prod".equalsIgnoreCase(normalize(appEnv));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
