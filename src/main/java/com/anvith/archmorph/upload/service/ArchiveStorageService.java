package com.anvith.archmorph.upload.service;

import com.anvith.archmorph.workspace.ProjectWorkspace;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

public interface ArchiveStorageService {

    /** Store the uploaded archive inside the workspace's input directory. */
    Path saveArchive(ProjectWorkspace workspace, MultipartFile file);
}
