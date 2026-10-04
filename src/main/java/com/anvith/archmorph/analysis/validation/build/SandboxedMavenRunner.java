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
 * Runs an <em>allowlisted</em> Maven command in a controlled child process.
 *
 * <ul>
 *   <li>the command line is assembled here from a fixed set of goals; nothing from the uploaded project
 *       (scripts, {@code mvnw}, {@code .mvn/}) is ever executed or read as configuration</li>
 *   <li>the environment is cleared; only PATH, JAVA_HOME, HOME (an isolated directory), MAVEN_OPTS,
 *       LANG and explicitly configured pass-through variables are set — no inherited credentials</li>
 *   <li>hard wall-clock timeout; the whole process tree is killed on expiry</li>
 *   <li>output is captured with a size cap and absolute paths are masked</li>
 *   <li>CPU-time and file-size limits through {@code prlimit} when it is available</li>
 * </ul>
 * <b>Limits of this sandbox:</b> it is process-level isolation, not a security boundary. Maven plugins and
 * annotation processors declared by the project still run with the privileges of the ArchMorph process.
 * Run ArchMorph itself in a disposable container if you analyse untrusted projects (see docs/THREAT_MODEL.md).
 */
@Component
public class SandboxedMavenRunner {

    private static final Logger log = LoggerFactory.getLogger(SandboxedMavenRunner.class);

    /** The only goals that may ever be invoked. */
    public enum Goal {
        COMPILE("compile"),
        TEST_COMPILE("test-compile"),
        TEST("test");

        private final String maven;

        Goal(String maven) {
            this.maven = maven;
        }
    }

    private final ArchMorphProperties properties;

    public SandboxedMavenRunner(ArchMorphProperties properties) {
        this.properties = properties;
    }

    public boolean mavenAvailable() {
        return SandboxedProcess.resolveExecutable(properties.getValidation().getBuild().getMavenExecutable()) != null;
    }

    public BuildResult run(Path projectDir, Path buildHome, Path localRepository, Goal goal) throws IOException {
        ArchMorphProperties.Build config = properties.getValidation().getBuild();
        Path executable = SandboxedProcess.resolveExecutable(config.getMavenExecutable());
        if (executable == null) {
            throw new IOException("Maven executable not found");
        }

        List<String> command = new ArrayList<>(SandboxedProcess.limits(config.getTimeout()));
        command.add(executable.toString());
        command.add("-B");
        command.add("--no-transfer-progress");
        command.add("-q");
        if (goal != Goal.TEST) {
            command.add("-DskipTests");
        }
        command.add("-Dmaven.repo.local=" + localRepository.toAbsolutePath());
        if (config.isOffline()) {
            command.add("-o");
        }
        command.add(goal.maven);

        Files.createDirectories(buildHome);
        SandboxedProcess.Execution execution = SandboxedProcess.execute(command, projectDir,
                sandboxEnvironment(config, buildHome), config.getTimeout(), config.getMaxOutputChars());
        log.info("Maven {} finished with exit code {} in {} ms{}", goal.maven, execution.exitCode(), execution.durationMillis(),
                execution.timedOut() ? " (timed out)" : "");

        List<String> shown = new ArrayList<>(List.of("mvn", "-B", "-q"));
        if (goal != Goal.TEST) {
            shown.add("-DskipTests");
        }
        shown.add(goal.maven);
        Map<String, String> masks = masks(projectDir, buildHome, localRepository);
        return new BuildResult(shown, execution.exitCode(),
                SandboxedProcess.mask(execution.stdout(), masks, config.getMaxOutputChars()),
                SandboxedProcess.mask(execution.stderr(), masks, config.getMaxOutputChars()),
                execution.durationMillis(), execution.timedOut(), execution.truncated());
    }

    static Map<String, String> masks(Path projectDir, Path buildHome, Path cache) {
        Map<String, String> masks = new LinkedHashMap<>();
        masks.put(projectDir.toAbsolutePath().toString(), "<project>");
        masks.put(buildHome.toAbsolutePath().toString(), "<home>");
        masks.put(cache.toAbsolutePath().toString(), "<repo>");
        masks.put(System.getProperty("java.home"), "<java>");
        masks.put(System.getProperty("user.home"), "~");
        return masks;
    }

    private Map<String, String> sandboxEnvironment(ArchMorphProperties.Build config, Path buildHome) {
        Map<String, String> env = new LinkedHashMap<>();
        String javaHome = System.getProperty("java.home");
        env.put("JAVA_HOME", javaHome);
        env.put("PATH", javaHome + "/bin:/usr/local/bin:/usr/bin:/bin");
        env.put("HOME", buildHome.toAbsolutePath().toString());
        env.put("MAVEN_OPTS", config.getMavenOpts());
        env.put("LANG", "C.UTF-8");
        for (String name : config.getPassthroughEnvironment()) {
            String value = System.getenv(name);
            if (value != null && name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                env.put(name, value);
            }
        }
        return env;
    }
}
