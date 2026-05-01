package com.genphish.campaign.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface ReferenceImageService {
    String store(UUID companyId, MultipartFile file);
    Resource loadAsResource(String fileName);
}
