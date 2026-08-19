package com.anvith.archmorph.upload.service;

import com.anvith.archmorph.common.config.WorkspaceProperties;
import com.anvith.archmorph.common.constants.WorkspaceConstants;
import com.anvith.archmorph.common.exception.WorkspaceCreationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@RequiredArgsConstructor
public class WorkspaceManager {

    private final WorkspaceProperties workspaceProperties;

    public Path createWorkspace(String projectId) {

        try {

            Path workspaceRoot = workspaceProperties.getRoot();

            createDirectory(workspaceRoot);

            createDirectory(workspaceRoot.resolve(WorkspaceConstants.ARCHIVES));
            createDirectory(workspaceRoot.resolve(WorkspaceConstants.PROJECTS));
            createDirectory(workspaceRoot.resolve(WorkspaceConstants.TEMP));
            createDirectory(workspaceRoot.resolve(WorkspaceConstants.LOGS));
            createDirectory(workspaceRoot.resolve(WorkspaceConstants.CACHE));

            Path projectRoot = workspaceRoot
                    .resolve(WorkspaceConstants.PROJECTS)
                    .resolve(projectId);

            createDirectory(projectRoot);

            createDirectory(projectRoot.resolve(WorkspaceConstants.ORIGINAL));
            createDirectory(projectRoot.resolve(WorkspaceConstants.WORKING));
            createDirectory(projectRoot.resolve(WorkspaceConstants.ANALYSIS));
            createDirectory(projectRoot.resolve(WorkspaceConstants.OUTPUT));
            createDirectory(projectRoot.resolve(WorkspaceConstants.REPORTS));

            return projectRoot;

        } catch (IOException e) {

            throw new WorkspaceCreationException(
                    "Unable to create workspace for project: " + projectId,
                    e
            );

        }

    }

    private void createDirectory(Path path) throws IOException {
        Files.createDirectories(path);
    }

}