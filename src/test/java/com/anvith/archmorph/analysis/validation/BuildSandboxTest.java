package com.anvith.archmorph.analysis.validation;

import com.anvith.archmorph.analysis.model.ProjectModel;
import com.anvith.archmorph.analysis.validation.build.BuildPluginGuard;
import com.anvith.archmorph.analysis.validation.build.SandboxedGradleRunner;
import com.anvith.archmorph.analysis.validation.build.SandboxedMavenRunner;
import com.anvith.archmorph.analysis.validation.level.BuildValidator;
import com.anvith.archmorph.common.config.ArchMorphProperties;
import com.anvith.archmorph.support.Projects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the process sandbox with a fake {@code mvn} that reports what it receives: cleared environment,
 * isolated HOME, allowlisted arguments, no wrapper or .mvn configuration, timeout and process-tree kill.
 */
class BuildSandboxTest {

    @TempDir
    Path temp;

    private ArchMorphProperties properties;

    @BeforeEach
    void setUp() {
        properties = new ArchMorphProperties();
        properties.getValidation().getBuild().setTimeout(Duration.ofSeconds(20));
    }

    private Path fakeMaven(String body) throws IOException {
        Path script = temp.resolve("fake-mvn");
        Files.writeString(script, "#!/bin/sh\n" + body + "\n");
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwx------"));
        properties.getValidation().getBuild().setMavenExecutable(script.toString());
        return script;
    }

    @Test
    void childProcessGetsAClearedEnvironmentAndAllowlistedArguments() throws Exception {
        fakeMaven("echo \"ARGS: $*\"; env; echo \"PWD: $(pwd)\"");
        SandboxedMavenRunner runner = new SandboxedMavenRunner(properties);
        Path project = Files.createDirectories(temp.resolve("project"));

        BuildResult result = runner.run(project, temp.resolve("home"), temp.resolve("repo"), SandboxedMavenRunner.Goal.COMPILE);

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).contains("ARGS: -B --no-transfer-progress -q -DskipTests -Dmaven.repo.local=<repo> compile");
        assertThat(result.stdout()).contains("HOME=<home>").contains("PWD: <project>");
        assertThat(result.stdout()).doesNotContain("JAVA_TOOL_OPTIONS", "HTTPS_PROXY", "HTTP_PROXY", "AWS_", "GITHUB_TOKEN");
        assertThat(result.stdout()).as("absolute paths are masked").doesNotContain(temp.toString());
        assertThat(result.command()).containsExactly("mvn", "-B", "-q", "-DskipTests", "compile");
    }

    @Test
    void timeoutKillsTheProcessTree() throws Exception {
        fakeMaven("sleep 60 & wait");
        properties.getValidation().getBuild().setTimeout(Duration.ofSeconds(1));
        SandboxedMavenRunner runner = new SandboxedMavenRunner(properties);
        long started = System.nanoTime();

        BuildResult result = runner.run(Files.createDirectories(temp.resolve("p")), temp.resolve("h"), temp.resolve("r"),
                SandboxedMavenRunner.Goal.TEST);

        assertThat(result.timedOut()).isTrue();
        assertThat(result.exitCode()).isEqualTo(-1);
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(15));
    }

    @Test
    void buildRunsOnACopyWithoutWrapperScriptsOrMavenConfig() throws Exception {
        fakeMaven("ls -a; ls -a .mvn 2>/dev/null; echo done");
        Path transformed = temp.resolve("transformed");
        ProjectModel model = Projects.model(transformed, Map.of("com/x/A.java", "package com.x; public class A {}"));
        Files.writeString(transformed.resolve("mvnw"), "#!/bin/sh\necho pwned");
        Files.createDirectories(transformed.resolve(".mvn"));
        Files.writeString(transformed.resolve(".mvn/jvm.config"), "-javaagent:/tmp/evil.jar");

        BuildValidator validator = new BuildValidator(properties, new SandboxedMavenRunner(properties), new SandboxedGradleRunner(properties), new BuildPluginGuard());
        Path scratch = Files.createDirectories(temp.resolve("scratch"));
        ValidationContext context = new ValidationContext(model, null, null, null, transformed, scratch, () -> null);

        BuildValidator.Outcome outcome = validator.validate(context, temp.resolve("repo"));

        assertThat(outcome.level().status()).isEqualTo(ValidationStatus.PASS);
        assertThat(outcome.build().stdout()).contains("pom.xml").contains("src").doesNotContain("mvnw", "jvm.config", ".mvn");
        assertThat(scratch).as("scratch copy removed").doesNotExist();
        assertThat(transformed.resolve("target")).as("transformed workspace untouched").doesNotExist();
    }

    @Test
    void projectsDeclaringCommandRunningPluginsAreNotBuilt() throws Exception {
        fakeMaven("echo SHOULD-NOT-RUN");
        Path transformed = temp.resolve("evil");
        ProjectModel model = Projects.model(transformed, Map.of("com/x/A.java", "package com.x; public class A {}"));
        Files.writeString(transformed.resolve("pom.xml"),
                "<project><build><plugins><plugin><artifactId>exec-maven-plugin</artifactId></plugin></plugins></build></project>");

        BuildValidator validator = new BuildValidator(properties, new SandboxedMavenRunner(properties), new SandboxedGradleRunner(properties), new BuildPluginGuard());
        BuildValidator.Outcome outcome = validator.validate(
                new ValidationContext(model, null, null, null, transformed, Files.createDirectories(temp.resolve("s")), () -> null),
                temp.resolve("repo"));

        assertThat(outcome.level().status()).isEqualTo(ValidationStatus.SKIPPED);
        assertThat(outcome.level().summary()).contains("exec-maven-plugin");
        assertThat(outcome.build()).isNull();
    }

    @Test
    void compilerErrorsAreParsedIntoIssuesWithProbableCauses() throws Exception {
        properties.getValidation().getBuild().setCompareWithOriginal(false); // parsing only; original == transformed here
        fakeMaven("echo \"[ERROR] $(pwd)/src/main/java/com/x/A.java:[3,8] package com.y does not exist\"; exit 1");
        Path transformed = temp.resolve("t");
        ProjectModel model = Projects.model(transformed, Map.of("com/x/A.java", "package com.x; public class A {}"));
        BuildValidator validator = new BuildValidator(properties, new SandboxedMavenRunner(properties), new SandboxedGradleRunner(properties), new BuildPluginGuard());

        BuildValidator.Outcome outcome = validator.validate(
                new ValidationContext(model, null, null, null, transformed, Files.createDirectories(temp.resolve("s2")), () -> null),
                temp.resolve("repo"));

        assertThat(outcome.level().status()).isEqualTo(ValidationStatus.FAIL);
        assertThat(outcome.level().issues()).singleElement().satisfies(issue -> {
            assertThat(issue.file()).isEqualTo("src/main/java/com/x/A.java");
            assertThat(issue.line()).isEqualTo(3);
            assertThat(issue.probableCause()).contains("package");
        });
    }

    private void fakeGradle(String body) throws IOException {
        Path script = temp.resolve("fake-gradle");
        Files.writeString(script, "#!/bin/sh\n" + body + "\n");
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwx------"));
        properties.getValidation().getBuild().setGradleExecutable(script.toString());
    }

    @Test
    void gradleBuildsAreSkippedUnlessEnabled() throws Exception {
        fakeGradle("echo SHOULD-NOT-RUN");
        Path transformed = temp.resolve("g");
        ProjectModel model = Projects.gradleModel(transformed, Map.of("com/x/A.java", "package com.x; public class A {}"));
        BuildValidator validator = new BuildValidator(properties, new SandboxedMavenRunner(properties),
                new SandboxedGradleRunner(properties), new BuildPluginGuard());

        BuildValidator.Outcome outcome = validator.validate(
                new ValidationContext(model, null, null, null, transformed, Files.createDirectories(temp.resolve("s3")), () -> null),
                temp.resolve("repo"));

        assertThat(outcome.level().status()).isEqualTo(ValidationStatus.SKIPPED);
        assertThat(outcome.level().summary()).contains("gradle-enabled");
        assertThat(outcome.build()).isNull();
    }

    @Test
    void enabledGradleRunsTheServerGradleOnACopyWithoutTheWrapper() throws Exception {
        fakeGradle("echo \"ARGS: $*\"; env; ls -a; ls gradle 2>/dev/null; echo done");
        properties.getValidation().getBuild().setGradleEnabled(true);
        Path transformed = temp.resolve("gw");
        ProjectModel model = Projects.gradleModel(transformed, Map.of("com/x/A.java", "package com.x; public class A {}"));
        Files.writeString(transformed.resolve("gradlew"), "#!/bin/sh\necho pwned");
        Files.createDirectories(transformed.resolve("gradle/wrapper"));
        Files.writeString(transformed.resolve("gradle/wrapper/gradle-wrapper.properties"), "distributionUrl=https://evil/x.zip");
        Files.createDirectories(transformed.resolve(".gradle"));
        BuildValidator validator = new BuildValidator(properties, new SandboxedMavenRunner(properties),
                new SandboxedGradleRunner(properties), new BuildPluginGuard());

        BuildValidator.Outcome outcome = validator.validate(
                new ValidationContext(model, null, null, null, transformed, Files.createDirectories(temp.resolve("s4")), () -> null),
                temp.resolve("cache/repo"));

        assertThat(outcome.level().status()).isEqualTo(ValidationStatus.PASS);
        String stdout = outcome.build().stdout();
        assertThat(stdout).contains("--no-daemon").contains("classes").contains("build.gradle").contains("settings.gradle");
        assertThat(stdout).contains("GRADLE_USER_HOME=").contains("HOME=<home>");
        assertThat(stdout).doesNotContain("gradlew", "gradle-wrapper", "wrapper", "HTTPS_PROXY", "GITHUB_TOKEN");
        assertThat(stdout.lines()).as("no project cache copied").doesNotContain(".gradle");
        assertThat(stdout).as("absolute paths are masked").doesNotContain(temp.toString());
        assertThat(outcome.build().command()).containsExactly("gradle", "--no-daemon", "-q", "classes");
    }

    @Test
    void gradleCompilerErrorsAreParsed() throws Exception {
        properties.getValidation().getBuild().setCompareWithOriginal(false); // parsing only; original == transformed here
        fakeGradle("echo \"$(pwd)/src/main/java/com/x/A.java:3: error: cannot find symbol\" >&2; echo 'FAILURE: Build failed' >&2; exit 1");
        properties.getValidation().getBuild().setGradleEnabled(true);
        Path transformed = temp.resolve("ge");
        ProjectModel model = Projects.gradleModel(transformed, Map.of("com/x/A.java", "package com.x; public class A {}"));
        BuildValidator validator = new BuildValidator(properties, new SandboxedMavenRunner(properties),
                new SandboxedGradleRunner(properties), new BuildPluginGuard());

        BuildValidator.Outcome outcome = validator.validate(
                new ValidationContext(model, null, null, null, transformed, Files.createDirectories(temp.resolve("s5")), () -> null),
                temp.resolve("repo"));

        assertThat(outcome.level().status()).isEqualTo(ValidationStatus.FAIL);
        assertThat(outcome.level().issues()).singleElement().satisfies(issue -> {
            assertThat(issue.file()).isEqualTo("src/main/java/com/x/A.java");
            assertThat(issue.line()).isEqualTo(3);
            assertThat(issue.message()).isEqualTo("cannot find symbol");
        });
    }

    @Test
    void eachCompilerErrorIsReportedOnce() throws Exception {
        // Maven prints every compiler error twice: in the compiler output and in the "Failed to execute goal" summary
        properties.getValidation().getBuild().setCompareWithOriginal(false);
        fakeMaven("for i in 1 2; do echo \"[ERROR] $(pwd)/src/main/java/com/x/A.java:[3,8] cannot find symbol\"; done; exit 1");
        Path transformed = temp.resolve("twice");
        ProjectModel model = Projects.model(transformed, Map.of("com/x/A.java", "package com.x; public class A {}"));

        BuildValidator.Outcome outcome = validator().validate(
                new ValidationContext(model, null, null, null, transformed, Files.createDirectories(temp.resolve("s6")), () -> null),
                temp.resolve("repo"));

        assertThat(outcome.level().issues()).hasSize(1);
        assertThat(outcome.level().summary()).isEqualTo("1 error(s), 0 warning(s)");
    }

    @Test
    void errorsTheOriginalAlreadyHasAreNotBlamedOnTheTransformation() throws Exception {
        // the same error in both builds (the transformed file moved, so only its name matches)
        fakeMaven("echo \"[ERROR] $(pwd)/src/main/java/com/x/$( [ -d src/main/java/com/x/a ] && echo a/ )A.java:[3,8] cannot find symbol\"; exit 1");
        Path original = temp.resolve("orig");
        ProjectModel model = Projects.model(original, Map.of("com/x/A.java", "package com.x; public class A {}"));
        Path transformed = temp.resolve("moved");
        Projects.model(transformed, Map.of("com/x/a/A.java", "package com.x.a; public class A {}"));

        BuildValidator.Outcome outcome = validator().validate(
                new ValidationContext(model, null, null, null, transformed, Files.createDirectories(temp.resolve("s7")), () -> null),
                temp.resolve("repo"));

        assertThat(outcome.level().status()).isEqualTo(ValidationStatus.WARN);
        assertThat(outcome.level().summary()).contains("original project already fails").contains("introduced none");
        assertThat(outcome.level().issues()).singleElement()
                .satisfies(issue -> assertThat(issue.probableCause()).contains("original project"));
    }

    @Test
    void errorsOnlyTheTransformedProjectHasStayErrors() throws Exception {
        // the original builds; the transformed copy (it has the moved file) does not
        fakeMaven("if [ -d src/main/java/com/x/a ]; then echo \"[ERROR] $(pwd)/src/main/java/com/x/a/A.java:[3,8] cannot find symbol\"; exit 1; fi; echo ok");
        Path original = temp.resolve("orig2");
        ProjectModel model = Projects.model(original, Map.of("com/x/A.java", "package com.x; public class A {}"));
        Path transformed = temp.resolve("moved2");
        Projects.model(transformed, Map.of("com/x/a/A.java", "package com.x.a; public class A {}"));

        BuildValidator.Outcome outcome = validator().validate(
                new ValidationContext(model, null, null, null, transformed, Files.createDirectories(temp.resolve("s8")), () -> null),
                temp.resolve("repo"));

        assertThat(outcome.level().status()).isEqualTo(ValidationStatus.FAIL);
        assertThat(outcome.level().summary()).contains("the original project builds");
        assertThat(outcome.level().issues()).singleElement()
                .satisfies(issue -> assertThat(issue.severity()).isEqualTo(ValidationIssue.Severity.ERROR));
    }

    private BuildValidator validator() {
        return new BuildValidator(properties, new SandboxedMavenRunner(properties), new SandboxedGradleRunner(properties),
                new BuildPluginGuard());
    }
}
