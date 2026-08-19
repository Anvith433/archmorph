package com.anvith.archmorph.upload.service;

import com.anvith.archmorph.common.config.WorkspaceProperties;
import com.anvith.archmorph.common.constants.WorkspaceConstants;
import com.anvith.archmorph.common.exception.ArchiveStorageException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Service
@RequiredArgsConstructor
public class ArchiveStorageServiceImpl implements ArchiveStorageService {

    private final WorkspaceProperties workspaceProperties;

    @Override
    public Path saveArchive(String projectId, MultipartFile file) {

        try {

            Path archiveDirectory = workspaceProperties.getRoot()
                    .resolve(WorkspaceConstants.ARCHIVES);

            Files.createDirectories(archiveDirectory);

            Path archiveFile =
                    archiveDirectory.resolve(projectId + ".zip");

            Files.copy(
                    file.getInputStream(),
                    archiveFile,
                    StandardCopyOption.REPLACE_EXISTING
            );

            return archiveFile;

        } catch (IOException e) {

            throw new ArchiveStorageException(
                    "Unable to save uploaded archive.",
                    e
            );

        }

    }

}