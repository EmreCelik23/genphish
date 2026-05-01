package com.genphish.campaign.service.impl;

import com.genphish.campaign.exception.InvalidOperationException;
import com.genphish.campaign.exception.ResourceNotFoundException;
import com.genphish.campaign.service.ReferenceImageService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class ReferenceImageServiceImpl implements ReferenceImageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("png", "jpg", "jpeg", "webp", "gif");

    private final Path storageDir;
    private final String publicBaseUrl;

    public ReferenceImageServiceImpl(
            @Value("${app.upload.reference-dir:./uploads/reference-images}") String storageDir,
            @Value("${app.public-base-url:http://localhost:8080}") String publicBaseUrl
    ) {
        this.storageDir = Path.of(storageDir).toAbsolutePath().normalize();
        this.publicBaseUrl = trimTrailingSlash(publicBaseUrl);
    }

    @PostConstruct
    void init() {
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
        Path targetPath = storageDir.resolve(fileName).normalize();

        if (!targetPath.startsWith(storageDir)) {
            throw new InvalidOperationException("Invalid storage path for uploaded file.");
        }

        try {
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("Failed to store reference image for company {}", companyId, e);
            throw new InvalidOperationException("Failed to store reference image file.");
        }

        return publicBaseUrl + "/api/v1/reference-images/" + fileName;
    }

    @Override
    public Resource loadAsResource(String fileName) {
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
}
