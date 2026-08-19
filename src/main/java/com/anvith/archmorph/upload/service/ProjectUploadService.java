package com.anvith.archmorph.upload.service;

import com.anvith.archmorph.upload.dto.UploadResponse;
import org.springframework.web.multipart.MultipartFile;

public interface ProjectUploadService {

    UploadResponse uploadProject(MultipartFile file);

}