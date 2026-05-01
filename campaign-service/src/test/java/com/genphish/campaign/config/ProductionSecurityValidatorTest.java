package com.genphish.campaign.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionSecurityValidatorTest {

    private ProductionSecurityValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ProductionSecurityValidator();
        ReflectionTestUtils.setField(validator, "failOnUnsafeDefaults", true);
        ReflectionTestUtils.setField(validator, "securityEnabled", true);
        ReflectionTestUtils.setField(validator, "requireS3InProd", true);
        ReflectionTestUtils.setField(validator, "referenceStorage", "s3");
        ReflectionTestUtils.setField(validator, "s3Bucket", "genphish-reference-images");
        ReflectionTestUtils.setField(validator, "apiToken", "abcdefghijklmnopqrstuvwxyz123456");
        ReflectionTestUtils.setField(validator, "aiServiceToken", "abcdefghijklmnopqrstuvwxyz654321");
        ReflectionTestUtils.setField(validator, "trackingSignatureSecret", "abcdefghijklmnopqrstuvwxyz!@#$12");
        ReflectionTestUtils.setField(validator, "oauthStateSecret", "abcdefghijklmnopqrstuvwxyz#$%^34");
        ReflectionTestUtils.setField(validator, "jwtEnabled", false);
        ReflectionTestUtils.setField(validator, "jwtSecret", "");
        ReflectionTestUtils.setField(validator, "jwtIssuer", "");
        ReflectionTestUtils.setField(validator, "jwtAudience", "");
    }

    @Test
    void run_ShouldSkipChecksOutsideProd() {
        ReflectionTestUtils.setField(validator, "appEnv", "local");
        assertThatCode(() -> validator.run(new DefaultApplicationArguments(new String[0])))
                .doesNotThrowAnyException();
    }

    @Test
    void run_ShouldFailInProdWhenUsingDefaultSecrets() {
        ReflectionTestUtils.setField(validator, "appEnv", "prod");
        ReflectionTestUtils.setField(validator, "apiToken", "genphish-dev-token");

        assertThatThrownBy(() -> validator.run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_API_TOKEN");
    }

    @Test
    void run_ShouldFailInProdWhenLocalStorageIsConfigured() {
        ReflectionTestUtils.setField(validator, "appEnv", "prod");
        ReflectionTestUtils.setField(validator, "referenceStorage", "local");

        assertThatThrownBy(() -> validator.run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("REFERENCE_IMAGE_STORAGE");
    }

    @Test
    void run_ShouldFailInProdWhenSecurityDisabled() {
        ReflectionTestUtils.setField(validator, "appEnv", "prod");
        ReflectionTestUtils.setField(validator, "securityEnabled", false);

        assertThatThrownBy(() -> validator.run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.security.enabled");
    }

    @Test
    void run_ShouldFailInProdWhenS3BucketMissing() {
        ReflectionTestUtils.setField(validator, "appEnv", "prod");
        ReflectionTestUtils.setField(validator, "referenceStorage", "s3");
        ReflectionTestUtils.setField(validator, "s3Bucket", " ");

        assertThatThrownBy(() -> validator.run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("REFERENCE_IMAGE_S3_BUCKET");
    }

    @Test
    void run_ShouldSkipProdChecksWhenFailFastDisabled() {
        ReflectionTestUtils.setField(validator, "appEnv", "prod");
        ReflectionTestUtils.setField(validator, "failOnUnsafeDefaults", false);
        ReflectionTestUtils.setField(validator, "securityEnabled", false);
        ReflectionTestUtils.setField(validator, "apiToken", "short");

        assertThatCode(() -> validator.run(new DefaultApplicationArguments(new String[0])))
                .doesNotThrowAnyException();
    }

    @Test
    void run_ShouldPassInProdWhenConfigIsSafe() {
        ReflectionTestUtils.setField(validator, "appEnv", "prod");

        assertThatCode(() -> validator.run(new DefaultApplicationArguments(new String[0])))
                .doesNotThrowAnyException();
    }

    @Test
    void run_ShouldFailInProdWhenJwtEnabledButIssuerMissing() {
        ReflectionTestUtils.setField(validator, "appEnv", "prod");
        ReflectionTestUtils.setField(validator, "jwtEnabled", true);
        ReflectionTestUtils.setField(validator, "jwtSecret", "abcdefghijklmnopqrstuvwxyzJWT123456");
        ReflectionTestUtils.setField(validator, "jwtIssuer", " ");
        ReflectionTestUtils.setField(validator, "jwtAudience", "genphish-api");

        assertThatThrownBy(() -> validator.run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_AUTH_ISSUER");
    }

    @Test
    void run_ShouldPassInProdWhenJwtConfigIsSafe() {
        ReflectionTestUtils.setField(validator, "appEnv", "prod");
        ReflectionTestUtils.setField(validator, "jwtEnabled", true);
        ReflectionTestUtils.setField(validator, "jwtSecret", "abcdefghijklmnopqrstuvwxyzJWT123456");
        ReflectionTestUtils.setField(validator, "jwtIssuer", "genphish-auth");
        ReflectionTestUtils.setField(validator, "jwtAudience", "genphish-api");

        assertThatCode(() -> validator.run(new DefaultApplicationArguments(new String[0])))
                .doesNotThrowAnyException();
    }
}
