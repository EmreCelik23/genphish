package com.genphish.campaign.controller;

import com.genphish.campaign.service.ReferenceImageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;

@RestController
@RequestMapping("/api/v1/reference-images")
@RequiredArgsConstructor
@Slf4j
public class ReferenceImageController {

    private final ReferenceImageService referenceImageService;

    @GetMapping("/{fileName:.+}")
    public ResponseEntity<Resource> getReferenceImage(@PathVariable String fileName) {
        Resource resource = referenceImageService.loadAsResource(fileName);
        String contentType = resolveContentType(resource);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource);
    }

    private String resolveContentType(Resource resource) {
        try {
            String detected = Files.probeContentType(resource.getFile().toPath());
            if (detected != null && !detected.isBlank()) {
                return detected;
            }
        } catch (IOException e) {
            log.debug("Failed to detect content type for reference image", e);
        }
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }
}
