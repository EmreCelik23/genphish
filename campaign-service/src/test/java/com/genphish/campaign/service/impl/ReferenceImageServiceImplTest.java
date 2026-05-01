package com.genphish.campaign.service.impl;

import com.genphish.campaign.exception.InvalidOperationException;
import com.genphish.campaign.exception.ResourceNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReferenceImageServiceImplTest {

    private Path tempDir;
    private ReferenceImageServiceImpl service;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("reference-image-test");
        service = new ReferenceImageServiceImpl(tempDir.toString(), "http://localhost:8080");
        service.init();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (tempDir != null && Files.exists(tempDir)) {
            try (var stream = Files.walk(tempDir)) {
                stream.sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException ignored) {
                            }
                        });
            }
        }
    }

    @Test
    void store_ShouldPersistImageAndReturnUrl() {
        UUID companyId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "login.png",
                "image/png",
                new byte[] {1, 2, 3}
        );

        String url = service.store(companyId, file);

        assertThat(url).startsWith("http://localhost:8080/api/v1/reference-images/");

        String fileName = url.substring(url.lastIndexOf('/') + 1);
        Path storedFile = tempDir.resolve(fileName);
        assertThat(Files.exists(storedFile)).isTrue();
    }

    @Test
    void store_ShouldRejectNonImageContentType() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "payload.txt",
                "text/plain",
                "not-image".getBytes()
        );

        assertThatThrownBy(() -> service.store(UUID.randomUUID(), file))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("Only image files are allowed");
    }

    @Test
    void loadAsResource_ShouldReturnExistingFile() throws IOException {
        Path imageFile = tempDir.resolve("abc.png");
        Files.write(imageFile, new byte[] {1, 2, 3});

        var resource = service.loadAsResource("abc.png");

        assertThat(resource.exists()).isTrue();
    }

    @Test
    void loadAsResource_ShouldThrowForMissingFile() {
        assertThatThrownBy(() -> service.loadAsResource("missing.png"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("ReferenceImage");
    }
}
