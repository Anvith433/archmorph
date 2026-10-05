package com.anvith.archmorph.analysis.validation.level;

import com.anvith.archmorph.analysis.validation.BuildResult;
import com.anvith.archmorph.analysis.validation.LevelResult;
import com.anvith.archmorph.analysis.validation.ValidationContext;
import com.anvith.archmorph.analysis.validation.ValidationIssue;
import com.anvith.archmorph.analysis.validation.ValidationLevel;
import com.anvith.archmorph.analysis.validation.ValidationStatus;
import com.anvith.archmorph.analysis.validation.build.BuildPluginGuard;
import com.anvith.archmorph.analysis.validation.build.SandboxedGradleRunner;
import com.anvith.archmorph.analysis.validation.build.SandboxedMavenRunner;
import com.anvith.archmorph.common.config.ArchMorphProperties;
import com.anvith.archmorph.workspace.WorkspaceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Level 7: Maven (or, when enabled, Gradle) compile or test of the transformed project, in a sandboxed child process
 * on a scratch <em>copy</em> of the project (the transformed workspace never receives build output).
 * Never runs uploaded wrappers or scripts; see {@link SandboxedMavenRunner} and {@link SandboxedGradleRunner}.
 */
@Component
public class BuildValidator {

    private static final Logger log = LoggerFactory.getLogger(BuildValidator.class);

    private static final Pattern COMPILER_ERROR =
            Pattern.compile("\\[ERROR\\]\\s+(?<file>\\S.*?\\.java):\\[(?<line>\\d+),(?<col>\\d+)\\]\\s+(?<msg>.*)");

    public record Outcome(LevelResult level, BuildResult build) {
    }

    /** {@code /path/File.java:12: error: cannot find symbol} — javac as printed by Gradle. */
    private static final Pattern JAVAC_ERROR =
            Pattern.compile("(?<file>\\S.*?\\.java):(?<line>\\d+):\\s+error:\\s+(?<msg>.*)");

    private final ArchMorphProperties properties;
    private final SandboxedMavenRunner runner;
    private final SandboxedGradleRunner gradleRunner;
    private final BuildPluginGuard guard;

    public BuildValidator(ArchMorphProperties properties, SandboxedMavenRunner runner, SandboxedGradleRunner gradleRunner,
                          BuildPluginGuard guard) {
        this.properties = properties;
        this.runner = runner;
        this.gradleRunner = gradleRunner;
        this.guard = guard;
    }

    public Outcome validate(ValidationContext context, Path mavenRepository) {
        ArchMorphProperties.Build config = properties.getValidation().getBuild();
        ValidationLevel level = ValidationLevel.BUILD;
        if (!config.isEnabled()) {
            return new Outcome(LevelResult.skipped(level, "Build validation is disabled in the configuration."), null);
        }
        boolean gradle = context.original().structure().buildTool() == com.anvith.archmorph.parser.ProjectStructure.BuildTool.GRADLE;
        if (gradle && !config.isGradleEnabled()) {
            return new Outcome(LevelResult.skipped(level, "Gradle build scripts are code; Gradle builds are disabled "
                    + "(archmorph.validation.build.gradle-enabled=false). Levels 1-6 do not execute uploaded code."), null);
        }
        if (gradle ? !gradleRunner.gradleAvailable() : !runner.mavenAvailable()) {
            return new Outcome(LevelResult.skipped(level, (gradle ? "Gradle" : "Maven") + " was not found on this server."), null);
        }

        Path projectDir = context.scratch().resolve("project");
        Path buildHome = context.scratch().resolve("home");
        long started = System.nanoTime();
        try {
            copyForBuild(context.transformedRoot(), projectDir);
            List<String> denied = gradle ? List.of() : guard.deniedPlugins(projectDir);
            if (!gradle && (!denied.isEmpty() || guard.hasEscapingModule(projectDir))) {
                return new Outcome(LevelResult.skipped(level, "The build was not run: the project declares build plugins that "
                        + "execute arbitrary commands (" + String.join(", ", denied) + ") or modules outside the project."), null);
            }

            boolean hasTests = !context.original().structure().testSourceRoots().isEmpty();
            var goal = switch (config.getMode()) {
                case TEST -> SandboxedMavenRunner.Goal.TEST;
                case COMPILE -> hasTests ? SandboxedMavenRunner.Goal.TEST_COMPILE : SandboxedMavenRunner.Goal.COMPILE;
            };
            Files.createDirectories(mavenRepository);
            BuildResult build = gradle
                    ? gradleRunner.run(projectDir, buildHome, gradleUserHome(mavenRepository), goal)
                    : runner.run(projectDir, buildHome, mavenRepository, goal);
            LevelResult result = evaluate(build, started);
            if (result.status() == ValidationStatus.FAIL && !build.timedOut() && config.isCompareWithOriginal()
                    && result.issues().stream().anyMatch(i -> i.file() != null)) {
                result = compareWithOriginal(context, result, gradle, goal, mavenRepository, started);
            }
            return new Outcome(result, build);
        } catch (IOException | RuntimeException e) {
            log.warn("Build validation could not run: {}", e.getClass().getSimpleName());
            return new Outcome(LevelResult.skipped(level, "The build could not be started."), null);
        } finally {
            WorkspaceManager.deleteRecursively(context.scratch());
        }
    }

    /** Shared Gradle cache next to the shared Maven repository, unless configured. */
    private Path gradleUserHome(Path mavenRepository) {
        String configured = properties.getValidation().getBuild().getGradleUserHome();
        return configured == null || configured.isBlank() ? mavenRepository.resolveSibling("gradle-home") : Path.of(configured);
    }

    private LevelResult evaluate(BuildResult build, long startedNanos) {
        long millis = (System.nanoTime() - startedNanos) / 1_000_000;
        ValidationLevel level = ValidationLevel.BUILD;
        String tool = !build.command().isEmpty() && "gradle".equals(build.command().getFirst()) ? "Gradle" : "Maven";
        if (build.timedOut()) {
            return new LevelResult(level, ValidationStatus.FAIL,
                    "The build exceeded the time limit and was stopped.", 1,
                    List.of(ValidationIssue.error(null, 0, tool + " timed out after " + (build.durationMillis() / 1000) + " s.",
                            "Increase archmorph.validation.build.timeout or check the project for long-running plugins.")), millis);
        }
        if (build.exitCode() == 0) {
            return new LevelResult(level, ValidationStatus.PASS,
                    String.join(" ", build.command()) + " succeeded in " + (build.durationMillis() / 1000.0) + " s", 0, List.of(), millis);
        }

        List<ValidationIssue> issues = new ArrayList<>(compilerIssues(build));
        String all = build.stdout() + "\n" + build.stderr();
        if (issues.isEmpty()) {
            boolean resolution = all.contains("Could not resolve") || all.contains("Non-resolvable")
                    || all.contains("Could not GET") || all.contains("No cached version")
                    || all.contains("offline mode") || all.contains("Failed to read artifact descriptor")
                    || all.contains("Could not transfer") || all.contains("Plugin ") && all.contains("could not be resolved");
            if (resolution) {
                return new LevelResult(level, ValidationStatus.SKIPPED,
                        tool + " could not resolve dependencies or plugins in the sandbox; the compile result is unknown.", 0,
                        List.of(), millis);
            }
            String first = all.lines().filter(l -> l.contains("[ERROR]") || l.startsWith("FAILURE:") || l.contains("What went wrong")).findFirst().orElse(tool + " reported a failure.").trim();
            issues.add(ValidationIssue.error(null, 0, truncate(first), "Gradle".equals(tool)
                    ? "No compiler error was reported. Check the captured output: a Gradle or plugin version mismatch "
                    + "with the project's wrapper fails the original project in the same way."
                    : "See the captured build output."));
        }
        return LevelResult.of(level, issues, "build succeeded", millis);
    }

    /**
     * Compiler errors of a build, without duplicates: Maven prints every error twice (in the compiler output and
     * again in the "Failed to execute goal" summary).
     */
    static List<ValidationIssue> compilerIssues(BuildResult build) {
        java.util.Map<String, ValidationIssue> issues = new java.util.LinkedHashMap<>();
        String all = build.stdout() + "\n" + build.stderr();
        for (Pattern pattern : List.of(JAVAC_ERROR, COMPILER_ERROR)) {
            Matcher matcher = pattern.matcher(all);
            while (matcher.find() && issues.size() < 50) {
                String file = matcher.group("file").replace("<project>/", "");
                int line = Integer.parseInt(matcher.group("line"));
                String message = matcher.group("msg").trim();
                issues.putIfAbsent(file + ":" + line + ":" + message, ValidationIssue.error(file, line, message, probableCause(message)));
            }
        }
        return List.copyOf(issues.values());
    }

    /**
     * The transformed project failed to compile: build the original the same way and mark the errors it already had,
     * so a failure the upload brought with it is not blamed on the transformation. Errors are matched by file name
     * and message (line numbers move when imports change).
     */
    private LevelResult compareWithOriginal(ValidationContext context, LevelResult transformed, boolean gradle,
                                            SandboxedMavenRunner.Goal goal, Path mavenRepository, long startedNanos)
            throws IOException {
        Path originalDir = context.scratch().resolve("original");
        copyForBuild(context.original().structure().projectRoot(), originalDir);
        Path home = context.scratch().resolve("home-original");
        BuildResult baseline = gradle
                ? gradleRunner.run(originalDir, home, gradleUserHome(mavenRepository), goal)
                : runner.run(originalDir, home, mavenRepository, goal);
        long millis = (System.nanoTime() - startedNanos) / 1_000_000;
        ValidationLevel level = ValidationLevel.BUILD;
        if (baseline.exitCode() == 0) {
            return new LevelResult(level, ValidationStatus.FAIL, transformed.summary()
                    + "; the original project builds, so these errors come from the transformation",
                    transformed.issueCount(), transformed.issues(), millis);
        }
        java.util.Map<String, Integer> before = new java.util.HashMap<>();
        for (ValidationIssue issue : compilerIssues(baseline)) {
            before.merge(matchKey(issue), 1, Integer::sum);
        }
        if (before.isEmpty()) {
            return transformed; // the original fails for another reason (e.g. dependencies); nothing to compare
        }
        List<ValidationIssue> issues = new ArrayList<>();
        int preExisting = 0;
        for (ValidationIssue issue : transformed.issues()) {
            String key = matchKey(issue);
            if (issue.severity() == ValidationIssue.Severity.ERROR && before.getOrDefault(key, 0) > 0) {
                before.merge(key, -1, Integer::sum);
                preExisting++;
                issues.add(ValidationIssue.warning(issue.file(), issue.line(), issue.message(),
                        "Already fails the same way in the original project; not caused by the transformation."));
            } else {
                issues.add(issue);
            }
        }
        int introduced = issues.size() - preExisting;
        if (introduced == 0) {
            return new LevelResult(level, ValidationStatus.WARN, "the original project already fails to build with the same "
                    + preExisting + " error(s); the transformation introduced none", issues.size(), List.copyOf(issues), millis);
        }
        return new LevelResult(level, ValidationStatus.FAIL, introduced + " error(s) introduced by the transformation; "
                + preExisting + " already present in the original project", issues.size(), List.copyOf(issues), millis);
    }

    private static String matchKey(ValidationIssue issue) {
        String file = issue.file() == null ? "" : issue.file().replace('\\', '/');
        return file.substring(file.lastIndexOf('/') + 1) + "|" + issue.message();
    }

    static String probableCause(String message) {
        String m = message.toLowerCase();
        if (m.contains("package") && m.contains("does not exist")) {
            return "An import or qualified name points to a package that no longer exists after the move.";
        }
        if (m.contains("cannot find symbol")) {
            return "A reference was not updated, or a package-private member is no longer visible after the move.";
        }
        if (m.contains("is not public") || m.contains("cannot be accessed from outside package")) {
            return "Package-private access across packages after the move; make the member public or keep the classes together.";
        }
        if (m.contains("already defined") || m.contains("duplicate class")) {
            return "Two classes ended up with the same qualified name.";
        }
        return "Compare with the original project: if it fails to compile in the same way, the cause is not the transformation.";
    }

    /** Copies the project without wrapper scripts and {@code .mvn/} (they can inject JVM options and extensions). */
    private void copyForBuild(Path source, Path target) throws IOException {
        try (Stream<Path> stream = Files.walk(source)) {
            for (Path file : (Iterable<Path>) stream::iterator) {
                Path relative = source.relativize(file);
                String normalized = relative.toString().replace('\\', '/');
                if (normalized.isEmpty() || normalized.equals(".mvn") || normalized.startsWith(".mvn/")
                        || normalized.endsWith("mvnw") || normalized.endsWith("mvnw.cmd")
                        || normalized.endsWith("gradlew") || normalized.endsWith("gradlew.bat")
                        || normalized.equals("gradle/wrapper") || normalized.startsWith("gradle/wrapper/")
                        || normalized.equals(".gradle") || normalized.startsWith(".gradle/")
                        || Files.isSymbolicLink(file)) {
                    continue;
                }
                Path destination = target.resolve(relative.toString());
                if (Files.isDirectory(file)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(file, destination);
                }
            }
        }
    }

    private static String truncate(String text) {
        return text.length() > 300 ? text.substring(0, 300) + "…" : text;
    }
}
