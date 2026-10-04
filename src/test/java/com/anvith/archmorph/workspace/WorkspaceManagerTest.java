package com.anvith.archmorph.workspace;

import com.anvith.archmorph.common.config.ArchMorphProperties;
import com.anvith.archmorph.common.exception.ArchMorphException;
import com.anvith.archmorph.common.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkspaceManagerTest {

    @TempDir
    Path temp;

    private WorkspaceManager manager;

    @BeforeEach
    void setUp() {
        ArchMorphProperties properties = new ArchMorphProperties();
        properties.getWorkspace().setRoot(temp.resolve("ws"));
        manager = new WorkspaceManager(properties);
    }

    @Test
    void createsIsolatedWorkspaceWithRandomId() {
        ProjectWorkspace a = manager.create();
        ProjectWorkspace b = manager.create();

        assertThat(a.projectId()).isNotEqualTo(b.projectId());
        assertThat(WorkspaceManager.isValidProjectId(a.projectId())).isTrue();
        assertThat(a.input()).isDirectory();
        assertThat(a.original()).isDirectory();
        assertThat(a.reports()).isDirectory();
        assertThat(a.root().getParent()).isEqualTo(manager.getProjectsRoot());
        assertThat(manager.get(a.projectId()).root()).isEqualTo(a.root());
    }

    @ParameterizedTest
    @ValueSource(strings = {"..", "../etc", "abc", "PROJECT-1234", "00000000-0000-0000-0000-00000000000G",
            "00000000-0000-0000-0000-000000000000/../x", ""})
    void rejectsMalformedIds(String id) {
        assertThatThrownBy(() -> manager.get(id)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void unknownButWellFormedIdIsNotFound() {
        assertThatThrownBy(() -> manager.get("12345678-1234-1234-1234-123456789abc")).isInstanceOf(NotFoundException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"../other", "a/../../b", "/etc/passwd", "C:/x", "", "x\0y"})
    void resolveInsideRejectsEscapes(String relative) {
        Path base = temp.resolve("base");
        assertThatThrownBy(() -> ProjectWorkspace.resolveInside(base, relative)).isInstanceOf(ArchMorphException.class);
    }

    @Test
    void resolveInsideAcceptsNestedPaths() {
        Path base = temp.resolve("base");
        assertThat(ProjectWorkspace.resolveInside(base, "src/main/java/A.java"))
                .isEqualTo(base.toAbsolutePath().normalize().resolve("src/main/java/A.java"));
    }

    @Test
    void deletionDoesNotFollowSymbolicLinks() throws IOException {
        ProjectWorkspace workspace = manager.create();
        Path outside = Files.writeString(temp.resolve("precious.txt"), "keep me");
        Files.createSymbolicLink(workspace.original().resolve("link"), outside);

        manager.delete(workspace.projectId());

        assertThat(workspace.root()).doesNotExist();
        assertThat(outside).exists().hasContent("keep me");
    }
}
