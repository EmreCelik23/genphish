package com.genphish.campaign.service.impl;

import com.genphish.campaign.exception.InvalidOperationException;
import com.genphish.campaign.exception.ResourceNotFoundException;
import com.genphish.campaign.service.ReferenceImageService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

@Service
@Slf4j
public class ReferenceImageServiceImpl implements ReferenceImageService {

    private enum StorageProvider {
        LOCAL,
        S3
    }

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("png", "jpg", "jpeg", "webp", "gif");

    private final Path storageDir;
    private final String publicBaseUrl;
    private final StorageProvider storageProvider;
    private final long retentionDays;
    private final String s3Bucket;
    private final String s3KeyPrefix;
    private final long s3PresignDurationSeconds;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Autowired
    public ReferenceImageServiceImpl(
            @Value("${app.upload.reference-dir:./uploads/reference-images}") String storageDir,
            @Value("${app.public-base-url:http://localhost:8080}") String publicBaseUrl,
            @Value("${app.upload.reference-storage:local}") String storageProvider,
            @Value("${app.upload.reference-retention-days:30}") long retentionDays,
            @Value("${app.upload.s3.bucket:}") String s3Bucket,
            @Value("${app.upload.s3.key-prefix:reference-images}") String s3KeyPrefix,
            @Value("${app.upload.s3.region:us-east-1}") String s3Region,
            @Value("${app.upload.s3.endpoint:}") String s3Endpoint,
            @Value("${app.upload.s3.path-style:true}") boolean s3PathStyle,
            @Value("${app.upload.s3.access-key:}") String s3AccessKey,
            @Value("${app.upload.s3.secret-key:}") String s3SecretKey,
            @Value("${app.upload.s3.presign-duration-seconds:1800}") long s3PresignDurationSeconds
    ) {
        this(
                storageDir,
                publicBaseUrl,
                storageProvider,
                retentionDays,
                s3Bucket,
                s3KeyPrefix,
                s3Region,
                s3Endpoint,
                s3PathStyle,
                s3AccessKey,
                s3SecretKey,
                s3PresignDurationSeconds,
                null,
                null
        );
    }

    ReferenceImageServiceImpl(
            String storageDir,
            String publicBaseUrl
    ) {
        this(
                storageDir,
                publicBaseUrl,
                "local",
                30,
                "",
                "reference-images",
                "us-east-1",
                "",
                true,
                "",
                "",
                1800,
                null,
                null
        );
    }

    ReferenceImageServiceImpl(
            String storageDir,
            String publicBaseUrl,
            String storageProvider,
            long retentionDays,
            String s3Bucket,
            String s3KeyPrefix,
            String s3Region,
            String s3Endpoint,
            boolean s3PathStyle,
            String s3AccessKey,
            String s3SecretKey,
            long s3PresignDurationSeconds,
            S3Client overrideS3Client,
            S3Presigner overrideS3Presigner
    ) {
        this.storageDir = Path.of(storageDir).toAbsolutePath().normalize();
        this.publicBaseUrl = trimTrailingSlash(publicBaseUrl);
        this.storageProvider = resolveStorageProvider(storageProvider);
        this.retentionDays = Math.max(retentionDays, 1L);
        this.s3Bucket = normalize(s3Bucket);
        this.s3KeyPrefix = normalize(s3KeyPrefix).isEmpty() ? "reference-images" : normalize(s3KeyPrefix);
        this.s3PresignDurationSeconds = Math.max(s3PresignDurationSeconds, 300L);

        if (this.storageProvider == StorageProvider.S3) {
            if (this.s3Bucket.isEmpty()) {
                throw new InvalidOperationException("S3 storage selected but app.upload.s3.bucket is empty.");
            }
            this.s3Client = overrideS3Client != null
                    ? overrideS3Client
                    : buildS3Client(s3Region, s3Endpoint, s3PathStyle, s3AccessKey, s3SecretKey);
            this.s3Presigner = overrideS3Presigner != null
                    ? overrideS3Presigner
                    : buildS3Presigner(s3Region, s3Endpoint, s3PathStyle, s3AccessKey, s3SecretKey);
        } else {
            this.s3Client = null;
            this.s3Presigner = null;
        }
    }

    @PostConstruct
    void init() {
        if (storageProvider != StorageProvider.LOCAL) {
            return;
        }
        try {
            Files.createDirectories(storageDir);
        } catch (IOException e) {
            throw new InvalidOperationException("Failed to initialize reference image storage directory.");
        }
    }

    @Override
    public String store(UUID companyId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidOperationException("Reference image file is required.");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new InvalidOperationException("Only image files are allowed for reference upload.");
        }

        String extension = resolveExtension(file.getOriginalFilename(), contentType);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new InvalidOperationException("Unsupported reference image format. Allowed: png, jpg, jpeg, webp, gif.");
        }

        String fileName = companyId + "_" + UUID.randomUUID() + "." + extension;
        return storageProvider == StorageProvider.S3
                ? storeInS3(companyId, file, fileName, contentType)
                : storeInLocal(file, fileName);
    }

    @Override
    public Resource loadAsResource(String fileName) {
        if (storageProvider != StorageProvider.LOCAL) {
            throw new InvalidOperationException("Reference image direct serving is available only in local storage mode.");
        }
        if (fileName == null || fileName.isBlank()) {
            throw new ResourceNotFoundException("ReferenceImage", "fileName", fileName);
        }

        Path path = storageDir.resolve(fileName).normalize();
        if (!path.startsWith(storageDir) || !Files.exists(path) || !Files.isRegularFile(path)) {
            throw new ResourceNotFoundException("ReferenceImage", "fileName", fileName);
        }

        try {
            return new UrlResource(path.toUri());
        } catch (MalformedURLException e) {
            throw new ResourceNotFoundException("ReferenceImage", "fileName", fileName);
        }
    }

    private String storeInLocal(MultipartFile file, String fileName) {
        Path targetPath = storageDir.resolve(fileName).normalize();
        if (!targetPath.startsWith(storageDir)) {
            throw new InvalidOperationException("Invalid storage path for uploaded file.");
        }

        try {
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            cleanupExpiredLocalFiles();
        } catch (IOException e) {
            log.error("Failed to store reference image on local storage", e);
            throw new InvalidOperationException("Failed to store reference image file.");
        }

        return publicBaseUrl + "/api/v1/reference-images/" + fileName;
    }

    private String storeInS3(UUID companyId, MultipartFile file, String fileName, String contentType) {
        if (s3Client == null || s3Presigner == null) {
            throw new InvalidOperationException("S3 storage is not initialized.");
        }

        String key = buildS3ObjectKey(companyId, fileName);
        Instant retentionUntil = Instant.now().plus(retentionDays, ChronoUnit.DAYS);

        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(s3Bucket)
                    .key(key)
                    .contentType(contentType)
                    .metadata(Map.of(
                            "company-id", companyId.toString(),
                            "retention-until", retentionUntil.toString()
                    ))
                    .build();
            long contentLength = file.getSize();
            if (contentLength > -1) {
                s3Client.putObject(putRequest, RequestBody.fromInputStream(file.getInputStream(), contentLength));
            } else {
                s3Client.putObject(putRequest, RequestBody.fromBytes(file.getBytes()));
            }
        } catch (Exception e) {
            log.error("Failed to upload reference image to S3 bucket={} key={}", s3Bucket, key, e);
            throw new InvalidOperationException("Failed to store reference image in object storage.");
        }

        return buildPresignedUrl(key);
    }

    private String buildPresignedUrl(String key) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(s3Bucket)
                .key(key)
                .build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(s3PresignDurationSeconds))
                .getObjectRequest(getObjectRequest)
                .build();
        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    private String buildS3ObjectKey(UUID companyId, String fileName) {
        String prefix = s3KeyPrefix.endsWith("/") ? s3KeyPrefix.substring(0, s3KeyPrefix.length() - 1) : s3KeyPrefix;
        return prefix + "/" + companyId + "/" + fileName;
    }

    private void cleanupExpiredLocalFiles() {
        Instant threshold = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        try (Stream<Path> stream = Files.list(storageDir)) {
            stream.filter(Files::isRegularFile).forEach(path -> {
                try {
                    Instant lastModified = Files.getLastModifiedTime(path).toInstant();
                    if (lastModified.isBefore(threshold)) {
                        Files.deleteIfExists(path);
                    }
                } catch (IOException e) {
                    log.debug("Failed to cleanup expired reference image: {}", path, e);
                }
            });
        } catch (IOException e) {
            log.debug("Failed to run reference image retention cleanup", e);
        }
    }

    private String resolveExtension(String originalFilename, String contentType) {
        if (originalFilename != null) {
            int lastDotIndex = originalFilename.lastIndexOf('.');
            if (lastDotIndex > -1 && lastDotIndex < originalFilename.length() - 1) {
                return originalFilename.substring(lastDotIndex + 1).toLowerCase(Locale.ROOT);
            }
        }

        String mime = contentType.toLowerCase(Locale.ROOT);
        if (mime.contains("png")) {
            return "png";
        }
        if (mime.contains("jpeg") || mime.contains("jpg")) {
            return "jpg";
        }
        if (mime.contains("webp")) {
            return "webp";
        }
        if (mime.contains("gif")) {
            return "gif";
        }
        return "png";
    }

    private StorageProvider resolveStorageProvider(String value) {
        String normalized = normalize(value).toLowerCase(Locale.ROOT);
        return "s3".equals(normalized) ? StorageProvider.S3 : StorageProvider.LOCAL;
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:8080";
        }
        String normalized = value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private S3Client buildS3Client(
            String region,
            String endpoint,
            boolean pathStyle,
            String accessKey,
            String secretKey
    ) {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(defaultIfBlank(region, "us-east-1")))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(pathStyle).build());

        if (!normalize(endpoint).isEmpty()) {
            builder = builder.endpointOverride(URI.create(endpoint.trim()));
        }

        if (!normalize(accessKey).isEmpty() && !normalize(secretKey).isEmpty()) {
            builder = builder.credentialsProvider(
                    StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey.trim(), secretKey.trim()))
            );
        } else {
            builder = builder.credentialsProvider(DefaultCredentialsProvider.create());
        }

        return builder.build();
    }

    private S3Presigner buildS3Presigner(
            String region,
            String endpoint,
            boolean pathStyle,
            String accessKey,
            String secretKey
    ) {
        S3Presigner.Builder builder = S3Presigner.builder()
                .region(Region.of(defaultIfBlank(region, "us-east-1")))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(pathStyle).build());

        if (!normalize(endpoint).isEmpty()) {
            builder = builder.endpointOverride(URI.create(endpoint.trim()));
        }

        if (!normalize(accessKey).isEmpty() && !normalize(secretKey).isEmpty()) {
            builder = builder.credentialsProvider(
                    StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey.trim(), secretKey.trim()))
            );
        } else {
            builder = builder.credentialsProvider(DefaultCredentialsProvider.create());
        }

        return builder.build();
    }

    private String defaultIfBlank(String value, String fallback) {
        return normalize(value).isEmpty() ? fallback : value.trim();
    }
}
