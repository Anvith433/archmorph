package com.anvith.archmorph.analysis.validation.build;

import com.anvith.archmorph.analysis.validation.BuildResult;
import com.anvith.archmorph.common.config.ArchMorphProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs the server's own Gradle (never the uploaded {@code gradlew}) on a copy of a Gradle project, with the same
 * process sandbox as Maven: cleared environment, isolated {@code HOME}, a dedicated {@code GRADLE_USER_HOME} (so no
 * user init scripts or credentials apply), no daemon, hard timeout, masked output.
 *
 * <p><b>Gradle build scripts are code.</b> Evaluating {@code build.gradle} runs whatever it contains with the
 * privileges of the ArchMorph process, which is why Gradle builds are disabled unless
 * {@code archmorph.validation.build.gradle-enabled=true}.</p>
 */
@Component
public class SandboxedGradleRunner {

    private static final Logger log = LoggerFactory.getLogger(SandboxedGradleRunner.class);

    private final ArchMorphProperties properties;

    public SandboxedGradleRunner(ArchMorphProperties properties) {
        this.properties = properties;
    }

    public boolean gradleAvailable() {
        return SandboxedProcess.resolveExecutable(properties.getValidation().getBuild().getGradleExecutable()) != null;
    }

    public BuildResult run(Path projectDir, Path buildHome, Path gradleUserHome, SandboxedMavenRunner.Goal goal) throws IOException {
        ArchMorphProperties.Build config = properties.getValidation().getBuild();
        Path executable = SandboxedProcess.resolveExecutable(config.getGradleExecutable());
        if (executable == null) {
            throw new IOException("Gradle executable not found");
        }
        String task = switch (goal) {
            case COMPILE -> "classes";
            case TEST_COMPILE -> "testClasses";
            case TEST -> "test";
        };
        List<String> command = new ArrayList<>(SandboxedProcess.limits(config.getTimeout()));
        command.add(executable.toString());
        command.addAll(List.of("--no-daemon", "--console=plain", "-q", "--project-cache-dir",
                buildHome.resolve("project-cache").toAbsolutePath().toString()));
        if (config.isOffline()) {
            command.add("--offline");
        }
        command.add(task);

        Files.createDirectories(buildHome);
        Files.createDirectories(gradleUserHome);
        Map<String, String> env = new LinkedHashMap<>();
        String javaHome = System.getProperty("java.home");
        env.put("JAVA_HOME", javaHome);
        env.put("PATH", javaHome + "/bin:/usr/local/bin:/usr/bin:/bin");
        env.put("HOME", buildHome.toAbsolutePath().toString());
        env.put("GRADLE_USER_HOME", gradleUserHome.toAbsolutePath().toString());
        env.put("GRADLE_OPTS", config.getGradleOpts());
        env.put("LANG", "C.UTF-8");
        for (String name : config.getPassthroughEnvironment()) {
            String value = System.getenv(name);
            if (value != null && name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                env.put(name, value);
            }
        }

        SandboxedProcess.Execution execution = SandboxedProcess.execute(command, projectDir, env, config.getTimeout(),
                config.getMaxOutputChars());
        log.info("Gradle {} finished with exit code {} in {} ms{}", task, execution.exitCode(), execution.durationMillis(),
                execution.timedOut() ? " (timed out)" : "");
        Map<String, String> masks = SandboxedMavenRunner.masks(projectDir, buildHome, gradleUserHome);
        return new BuildResult(List.of("gradle", "--no-daemon", "-q", task), execution.exitCode(),
                SandboxedProcess.mask(execution.stdout(), masks, config.getMaxOutputChars()),
                SandboxedProcess.mask(execution.stderr(), masks, config.getMaxOutputChars()),
                execution.durationMillis(), execution.timedOut(), execution.truncated());
    }
}
