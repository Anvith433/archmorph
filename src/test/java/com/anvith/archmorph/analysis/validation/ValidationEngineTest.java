package com.anvith.archmorph.analysis.validation;

import com.anvith.archmorph.pipeline.Deadline;
import com.anvith.archmorph.pipeline.ProgressListener;
import com.anvith.archmorph.support.ArchMorphTest;
import com.anvith.archmorph.support.PipelineRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/** Each validation level must detect the corruption it is responsible for. */
@ArchMorphTest
class ValidationEngineTest {

    private static final String USER_SERVICE = "src/main/java/com/demo/modules/user/service/UserService.java";

    @Autowired
    PipelineRunner runner;

    @Autowired
    ValidationEngine engine;

    @TempDir
    Path temp;

    private ValidationReport tamper(Consumer<Path> corruption) throws Exception {
        PipelineRunner.Run run = runner.run("spring-layered", temp);
        assertThat(run.validation().status()).isNotEqualTo(ValidationStatus.FAIL);
        corruption.accept(run.transformed());
        return engine.validate(run.analysis().model(), run.analysis().graph(), run.plan(), run.analysis().suggestion(),
                run.transformed(), temp.resolve("scratch2"), ProgressListener.NONE, Deadline.never());
    }

    private static LevelResult level(ValidationReport report, ValidationLevel level) {
        return report.levels().stream().filter(l -> l.level() == level).findFirst().orElseThrow();
    }

    @Test
    void missingFileFailsFilesystemLevel() throws Exception {
        ValidationReport report = tamper(root -> delete(root.resolve(USER_SERVICE)));
        assertThat(level(report, ValidationLevel.FILESYSTEM).status()).isEqualTo(ValidationStatus.FAIL);
        assertThat(report.status()).isEqualTo(ValidationStatus.FAIL);
    }

    @Test
    void missingResourceFailsFilesystemLevel() throws Exception {
        ValidationReport report = tamper(root -> delete(root.resolve("src/main/resources/application.properties")));
        assertThat(level(report, ValidationLevel.FILESYSTEM).issues())
                .anyMatch(i -> "src/main/resources/application.properties".equals(i.file()));
    }

    @Test
    void wrongPackageFailsPackageConsistency() throws Exception {
        ValidationReport report = tamper(root -> replace(root.resolve(USER_SERVICE),
                "package com.demo.modules.user.service;", "package com.demo.service;"));
        assertThat(level(report, ValidationLevel.PACKAGE_CONSISTENCY).status()).isEqualTo(ValidationStatus.FAIL);
    }

    @Test
    void staleImportFailsImportResolution() throws Exception {
        ValidationReport report = tamper(root -> replace(root.resolve(USER_SERVICE),
                "import com.demo.modules.user.dto.UserDto;", "import com.demo.dto.UserDto;"));
        LevelResult imports = level(report, ValidationLevel.IMPORT_RESOLUTION);
        assertThat(imports.status()).isEqualTo(ValidationStatus.FAIL);
        assertThat(imports.issues()).anyMatch(i -> i.message().contains("old location") && i.line() > 0);
        assertThat(level(report, ValidationLevel.DEPENDENCY_GRAPH).status()).isEqualTo(ValidationStatus.FAIL);
    }

    @Test
    void syntaxErrorFailsParsingAndSkipsTheBuild() throws Exception {
        ValidationReport report = tamper(root -> replace(root.resolve(USER_SERVICE), "public class UserService {", "public class UserService {{"));
        assertThat(level(report, ValidationLevel.JAVA_PARSING).status()).isEqualTo(ValidationStatus.FAIL);
        assertThat(level(report, ValidationLevel.BUILD).status()).isEqualTo(ValidationStatus.SKIPPED);
    }

    @Test
    void sharedCodeDependingOnAModuleIsAnArchitectureWarning() throws Exception {
        ValidationReport report = tamper(root -> { });
        LevelResult architecture = level(report, ValidationLevel.ARCHITECTURE_RULES);
        assertThat(architecture.status()).isEqualTo(ValidationStatus.WARN);
        assertThat(architecture.issues()).singleElement()
                .satisfies(i -> assertThat(i.message()).contains("GlobalExceptionHandler").contains("user"));
    }

    private static void delete(Path file) {
        try {
            Files.delete(file);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void replace(Path file, String from, String to) {
        try {
            String text = Files.readString(file);
            assertThat(text).contains(from);
            Files.writeString(file, text.replace(from, to));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
