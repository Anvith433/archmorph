package com.anvith.archmorph.upload.service;

import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

public interface ArchiveStorageService {

    Path saveArchive(String projectId, MultipartFile file);

}