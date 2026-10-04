package com.anvith.archmorph.workspace;

import com.anvith.archmorph.common.config.ArchMorphProperties;
import com.anvith.archmorph.common.exception.ErrorCode;
import com.anvith.archmorph.common.exception.NotFoundException;
import com.anvith.archmorph.common.exception.WorkspaceCreationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Creates, resolves and deletes isolated project workspaces.
 *
 * <p>Project IDs are random UUIDs generated server-side. Every lookup
 * validates the ID format and verifies the resolved directory is a direct
 * child of the projects root, so one project ID can never reach another
 * project's files or anything outside the workspace.</p>
 */
@Service
public class WorkspaceManager {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceManager.class);

    private static final Pattern PROJECT_ID = Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    private final Path projectsRoot;

    public WorkspaceManager(ArchMorphProperties properties) {
        this.projectsRoot = properties.getWorkspace().getRoot().toAbsolutePath().normalize().resolve("projects");
    }

    public Path getProjectsRoot() {
        return projectsRoot;
    }

    public ProjectWorkspace create() {
        String projectId = UUID.randomUUID().toString();
        Path root = projectsRoot.resolve(projectId);
        ProjectWorkspace workspace = new ProjectWorkspace(projectId, root);
        try {
            Files.createDirectories(projectsRoot);
            Files.createDirectory(root);
            restrictPermissions(root);
            for (Path dir : List.of(workspace.input(), workspace.original(), workspace.reports(),
                    workspace.metadata(), workspace.buildHome())) {
                Files.createDirectories(dir);
            }
            return workspace;
        } catch (IOException e) {
            throw new WorkspaceCreationException("Unable to create an isolated workspace.", e);
        }
    }

    public static boolean isValidProjectId(String projectId) {
        return projectId != null && PROJECT_ID.matcher(projectId).matches();
    }

    /** Resolve an existing workspace; throws PROJECT_NOT_FOUND for unknown or malformed IDs. */
    public ProjectWorkspace get(String projectId) {
        if (!isValidProjectId(projectId)) {
            throw new NotFoundException(ErrorCode.PROJECT_NOT_FOUND, "Project not found.");
        }
        Path root = projectsRoot.resolve(projectId).normalize();
        if (!root.getParent().equals(projectsRoot) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new NotFoundException(ErrorCode.PROJECT_NOT_FOUND, "Project not found.");
        }
        return new ProjectWorkspace(projectId, root);
    }

    public boolean exists(String projectId) {
        return isValidProjectId(projectId) && Files.isDirectory(projectsRoot.resolve(projectId), LinkOption.NOFOLLOW_LINKS);
    }

    public List<String> listProjectIds() {
        if (!Files.isDirectory(projectsRoot)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(projectsRoot)) {
            return stream.map(p -> p.getFileName().toString()).filter(WorkspaceManager::isValidProjectId).toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    public void delete(String projectId) {
        if (!exists(projectId)) {
            return;
        }
        deleteRecursively(projectsRoot.resolve(projectId));
        log.info("Deleted workspace of project {}", projectId);
    }

    /** Delete a directory tree without following symbolic links. */
    public static void deleteRecursively(Path directory) {
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try {
            Files.walkFileTree(directory, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.warn("Could not fully delete a workspace directory: {}", e.getClass().getSimpleName());
        }
    }

    /** Remove and recreate an empty directory inside a workspace. */
    public static void resetDirectory(Path directory) {
        deleteRecursively(directory);
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new WorkspaceCreationException("Unable to prepare workspace directory.", e);
        }
    }

    private void restrictPermissions(Path root) {
        try {
            Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Non-POSIX file system: rely on the workspace root's permissions.
        }
    }

    public List<String> expiredProjects(java.time.Instant cutoff) {
        List<String> expired = new ArrayList<>();
        for (String id : listProjectIds()) {
            try {
                if (Files.getLastModifiedTime(projectsRoot.resolve(id)).toInstant().isBefore(cutoff)) {
                    expired.add(id);
                }
            } catch (IOException ignored) {
                // Skip unreadable entries.
            }
        }
        return expired;
    }
}
