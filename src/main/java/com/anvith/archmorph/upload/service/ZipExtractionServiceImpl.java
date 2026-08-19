package com.anvith.archmorph.upload.service;

import com.anvith.archmorph.common.config.WorkspaceProperties;
import com.anvith.archmorph.common.constants.WorkspaceConstants;
import com.anvith.archmorph.common.exception.ProjectExtractionException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
@RequiredArgsConstructor
public class ZipExtractionServiceImpl implements ZipExtractionService {

    private final WorkspaceProperties workspaceProperties;

    @Override
    public void extractArchive(String projectId, Path archivePath) {

        Path destination = workspaceProperties.getRoot()
                .resolve(WorkspaceConstants.PROJECTS)
                .resolve(projectId)
                .resolve(WorkspaceConstants.ORIGINAL);

        try {

            Files.createDirectories(destination);

            try (InputStream inputStream = Files.newInputStream(archivePath);
                 ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {

                ZipEntry entry;

                while ((entry = zipInputStream.getNextEntry()) != null) {

                    Path target = destination.resolve(entry.getName()).normalize();

                    if (entry.isDirectory()) {

                        Files.createDirectories(target);

                    } else {

                        Files.createDirectories(target.getParent());

                        Files.copy(
                                zipInputStream,
                                target,
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING
                        );

                    }

                    zipInputStream.closeEntry();

                }

            }

        } catch (IOException e) {

            throw new ProjectExtractionException(
                    "Unable to extract uploaded project.",
                    e
            );

        }

    }

}