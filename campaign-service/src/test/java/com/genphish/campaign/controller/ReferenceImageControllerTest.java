package com.genphish.campaign.controller;

import com.genphish.campaign.service.ReferenceImageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ReferenceImageControllerTest {

    @Mock
    private ReferenceImageService referenceImageService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ReferenceImageController controller = new ReferenceImageController(referenceImageService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void getReferenceImage_ShouldReturnResource() throws Exception {
        byte[] payload = new byte[] {1, 2, 3, 4};
        ByteArrayResource resource = new ByteArrayResource(payload);

        when(referenceImageService.loadAsResource("test.png")).thenReturn(resource);

        mockMvc.perform(get("/api/v1/reference-images/test.png"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/octet-stream"))
                .andExpect(content().bytes(payload));
    }
}
