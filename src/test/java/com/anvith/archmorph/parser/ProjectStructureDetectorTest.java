package com.anvith.archmorph.parser;

import com.anvith.archmorph.support.Fixtures;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectStructureDetectorTest {

    private final ProjectStructureDetector detector = new ProjectStructureDetector();

    @Test
    void gradleIncludesAreReadWithoutEvaluatingTheScript() {
        assertThat(ProjectStructureDetector.gradleSubprojects("include 'app', ':services:order'"))
                .containsExactly("app", "services/order");
        assertThat(ProjectStructureDetector.gradleSubprojects("include(\"app\")\ninclude(\":core\")"))
                .containsExactly("app", "core");
        assertThat(ProjectStructureDetector.gradleSubprojects("include(\n    \"core\",\n    \"web\",\n)\nrootProject.name = \"x\""))
                .containsExactly("core", "web");
        assertThat(ProjectStructureDetector.gradleSubprojects("include 'a',\n        'b'\nrootProject.name = 'c'"))
                .containsExactly("a", "b");
        assertThat(ProjectStructureDetector.gradleSubprojects("includeBuild(\"build-logic\")\ninclude '../escape'"))
                .as("included builds and paths leaving the project are ignored").isEmpty();
    }

    @Test
    void nestedMavenAggregatorsContributeTheirModules() {
        ProjectStructure structure = detector.detect(Fixtures.path("multi-module-maven"));

        assertThat(structure.buildTool()).isEqualTo(ProjectStructure.BuildTool.MAVEN);
        assertThat(structure.multiModule()).isTrue();
        assertThat(structure.mavenModules()).containsExactly("libs", "shop-app", "libs/shop-core");
        assertThat(structure.mainSourceRoots()).extracting(root -> relative(structure, root))
                .containsExactlyInAnyOrder("shop-app/src/main/java", "libs/shop-core/src/main/java");
    }

    @Test
    void gradleSingleProjectIsDetected() {
        ProjectStructure structure = detector.detect(Fixtures.path("gradle-layered"));

        assertThat(structure.buildTool()).isEqualTo(ProjectStructure.BuildTool.GRADLE);
        assertThat(structure.multiModule()).isFalse();
        assertThat(structure.artifactId()).isEqualTo("library");
        assertThat(structure.groupId()).isEqualTo("com.library");
        assertThat(structure.warnings()).anyMatch(w -> w.contains("never executed"));
    }

    @Test
    void gradleMultiProjectSourceRootsComeFromTheSettingsFile() {
        ProjectStructure structure = detector.detect(Fixtures.path("gradle-multi-project"));

        assertThat(structure.buildTool()).isEqualTo(ProjectStructure.BuildTool.GRADLE);
        assertThat(structure.mavenModules()).containsExactly("clinic-core", "clinic-app");
        assertThat(structure.artifactId()).isEqualTo("clinic");
        assertThat(structure.mainSourceRoots()).extracting(root -> relative(structure, root))
                .containsExactlyInAnyOrder("clinic-core/src/main/java", "clinic-app/src/main/java");
        assertThat(structure.testSourceRoots()).extracting(root -> relative(structure, root))
                .containsExactly("clinic-core/src/test/java");
    }

    private static String relative(ProjectStructure structure, Path root) {
        return structure.projectRoot().relativize(root).toString().replace('\\', '/');
    }
}
