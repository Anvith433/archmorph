package com.anvith.archmorph.analysis.validation.build;

import com.anvith.archmorph.analysis.validation.BuildResult;
import com.anvith.archmorph.common.config.ArchMorphProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
        return resolveExecutable(properties.getValidation().getBuild().getMavenExecutable()) != null;
    }

    public BuildResult run(Path projectDir, Path buildHome, Path localRepository, Goal goal) throws IOException {
        ArchMorphProperties.Build config = properties.getValidation().getBuild();
        Path executable = resolveExecutable(config.getMavenExecutable());
        if (executable == null) {
            throw new IOException("Maven executable not found");
        }

        List<String> command = new ArrayList<>();
        Path prlimit = findOnPath("prlimit");
        if (prlimit != null) {
            long cpuSeconds = Math.max(60, config.getTimeout().toSeconds() * 2);
            command.addAll(List.of(prlimit.toString(), "--cpu=" + cpuSeconds, "--fsize=" + (2L << 30), "--"));
        }
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
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(projectDir.toFile())
                .redirectErrorStream(false);
        builder.environment().clear();
        builder.environment().putAll(sandboxEnvironment(config, buildHome));

        long started = System.nanoTime();
        Process process = builder.start();
        process.getOutputStream().close();

        StringBuilder out = new StringBuilder();
        StringBuilder err = new StringBuilder();
        AtomicBoolean truncated = new AtomicBoolean();
        Thread outReader = reader(process.getInputStream(), out, config.getMaxOutputChars(), truncated);
        Thread errReader = reader(process.getErrorStream(), err, config.getMaxOutputChars(), truncated);

        boolean finished;
        try {
            finished = process.waitFor(config.getTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            finished = false;
        }
        boolean timedOut = !finished;
        if (timedOut) {
            killTree(process);
        }
        try {
            outReader.join(2000);
            errReader.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        int exit = timedOut ? -1 : process.exitValue();
        long millis = (System.nanoTime() - started) / 1_000_000;
        log.info("Maven {} finished with exit code {} in {} ms{}", goal.maven, exit, millis, timedOut ? " (timed out)" : "");

        List<String> shown = new ArrayList<>(List.of("mvn", "-B", "-q"));
        if (goal != Goal.TEST) {
            shown.add("-DskipTests");
        }
        shown.add(goal.maven);
        return new BuildResult(shown, exit,
                sanitize(out.toString(), projectDir, buildHome, localRepository),
                sanitize(err.toString(), projectDir, buildHome, localRepository),
                millis, timedOut, truncated.get());
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

    private Thread reader(InputStream stream, StringBuilder sink, int max, AtomicBoolean truncated) {
        Thread thread = new Thread(() -> {
            byte[] buffer = new byte[8192];
            try (InputStream in = stream) {
                int read;
                while ((read = in.read(buffer)) != -1) {
                    synchronized (sink) {
                        if (sink.length() < max) {
                            sink.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
                        } else {
                            truncated.set(true);
                        }
                    }
                }
            } catch (IOException ignored) {
                // stream closed by process termination
            }
        }, "archmorph-build-output");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private void killTree(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }

    private String sanitize(String text, Path projectDir, Path buildHome, Path repo) {
        String result = text;
        result = replaceAll(result, projectDir.toAbsolutePath().toString(), "<project>");
        result = replaceAll(result, buildHome.toAbsolutePath().toString(), "<home>");
        result = replaceAll(result, repo.toAbsolutePath().toString(), "<repo>");
        result = replaceAll(result, System.getProperty("java.home"), "<java>");
        result = replaceAll(result, System.getProperty("user.home"), "~");
        if (result.length() > properties.getValidation().getBuild().getMaxOutputChars()) {
            result = result.substring(0, properties.getValidation().getBuild().getMaxOutputChars());
        }
        return result;
    }

    private static String replaceAll(String text, String needle, String replacement) {
        return needle == null || needle.isBlank() || needle.length() < 2 ? text : text.replace(needle, replacement);
    }

    private Path resolveExecutable(String configured) {
        if (configured == null || configured.isBlank() || !configured.matches("[A-Za-z0-9_./\\\\:-]+")) {
            return null;
        }
        Path candidate = Path.of(configured);
        if (candidate.isAbsolute()) {
            return Files.isExecutable(candidate) ? candidate : null;
        }
        return findOnPath(configured);
    }

    private Path findOnPath(String name) {
        String path = System.getenv("PATH");
        if (path == null) {
            return null;
        }
        for (String directory : path.split(java.io.File.pathSeparator)) {
            Path candidate = Path.of(directory).resolve(name);
            if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                return candidate;
            }
        }
        return null;
    }
}
