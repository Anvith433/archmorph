package com.anvith.archmorph.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Strongly typed configuration for every ArchMorph subsystem.
 *
 * <p>All limits, weights and security settings live here so that no magic
 * constants are scattered through the code base. Defaults are safe for a
 * local, single-user installation; see {@code docs/THREAT_MODEL.md} before
 * exposing ArchMorph to other users.</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "archmorph")
public class ArchMorphProperties {

    private Workspace workspace = new Workspace();
    private Upload upload = new Upload();
    private Analysis analysis = new Analysis();
    private ModuleDiscovery moduleDiscovery = new ModuleDiscovery();
    private Transformation transformation = new Transformation();
    private Validation validation = new Validation();
    private Security security = new Security();
    private Jobs jobs = new Jobs();

    @Getter
    @Setter
    public static class Workspace {
        /** Root directory holding every isolated project workspace. */
        private Path root = Path.of("workspace");
        /** Projects older than this are deleted by the retention task. */
        private Duration retention = Duration.ofHours(24);
        /** How often the retention task runs. */
        private Duration cleanupInterval = Duration.ofMinutes(15);
    }

    @Getter
    @Setter
    public static class Upload {
        private DataSize maxArchiveSize = DataSize.ofMegabytes(100);
        private DataSize maxUncompressedSize = DataSize.ofMegabytes(500);
        private int maxEntryCount = 20_000;
        private DataSize maxSingleEntrySize = DataSize.ofMegabytes(50);
        /** Entries above this uncompressed/compressed ratio (and above 1 MB) are treated as bombs. */
        private int maxCompressionRatio = 200;
        /** Directory names skipped during extraction (generated or IDE content). */
        private List<String> ignoredDirectories = new ArrayList<>(List.of(
                ".git", ".svn", ".hg", "target", "build", "out", ".gradle",
                ".idea", ".vscode", ".settings", "node_modules", "__MACOSX"));
        /** File names skipped during extraction. */
        private List<String> ignoredFiles = new ArrayList<>(List.of(".DS_Store", "Thumbs.db"));
    }

    @Getter
    @Setter
    public static class Analysis {
        private int maxJavaFiles = 10_000;
        private int maxProjectFiles = 20_000;
        private Duration timeout = Duration.ofSeconds(300);
        /** Use the JavaParser symbol solver as a secondary resolver for expression types. */
        private boolean symbolSolverEnabled = true;
    }

    /**
     * Weights of the module affinity model. They are normalised to sum to 1
     * at runtime; see docs/ARCHITECTURE.md#module-discovery.
     */
    @Getter
    @Setter
    public static class ModuleDiscovery {
        private double dependencyWeight = 0.30;
        private double namingWeight = 0.20;
        private double packageWeight = 0.10;
        private double entityWeight = 0.20;
        private double endpointWeight = 0.10;
        private double typeUsageWeight = 0.10;
        /** Subtracted when two classes carry different, known domain terms. */
        private double crossDomainPenalty = 0.25;
        /** Minimum cluster affinity needed to place a class into a module automatically. */
        private double minAssignmentScore = 0.20;
        /** Classes used by at least this many modules (and owned by none) become shared. */
        private int sharedUsageThreshold = 3;
        /** Modules with more classes than this fraction of all business classes are flagged. */
        private double oversizedModuleFraction = 0.45;
        private int oversizedModuleMinClasses = 25;
        private double lowCohesionThreshold = 0.30;
        private double highCouplingThreshold = 0.60;
    }

    @Getter
    @Setter
    public static class Transformation {
        /** Validation must have run before the transformed project can be downloaded. */
        private boolean requireValidation = true;
        /** Move tests that mirror moved production classes, rewrite the rest. */
        private boolean preserveTests = true;
        private Duration timeout = Duration.ofSeconds(300);
        /** Name of the package segment that holds business modules. */
        private String modulesPackage = "modules";
        /** Name of the package segment that holds shared code. */
        private String sharedPackage = "shared";
    }

    @Getter
    @Setter
    public static class Validation {
        private Build build = new Build();
    }

    @Getter
    @Setter
    public static class Build {
        /**
         * Run Maven against the transformed project. Maven executes plugins
         * declared by the uploaded pom, so only enable this when you trust
         * the uploaded code or run ArchMorph inside a disposable container.
         */
        private boolean enabled = true;
        /** COMPILE runs {@code mvn -DskipTests compile}; TEST runs {@code mvn test}. */
        private BuildMode mode = BuildMode.COMPILE;
        /** Maven executable from the host. The uploaded mvnw is never executed. */
        private String mavenExecutable = "mvn";
        private Duration timeout = Duration.ofSeconds(240);
        /** Run Maven in offline mode (no downloads). */
        private boolean offline = false;
        /**
         * Maven local repository used for validation builds. Blank = a dedicated directory inside the
         * ArchMorph workspace (never the user's ~/.m2: a malicious pom could poison a shared cache).
         */
        private String localRepository = "";
        /** Environment variables copied into the build process; credentials are never forwarded by default. */
        private List<String> passthroughEnvironment = new ArrayList<>();
        /** JVM options passed to Maven via MAVEN_OPTS. */
        private String mavenOpts = "-Xmx768m";
        /** Captured stdout/stderr are truncated to this many characters. */
        private int maxOutputChars = 64_000;
    }

    public enum BuildMode {
        COMPILE,
        TEST
    }

    @Getter
    @Setter
    public static class Security {
        /** Frontend origins allowed by CORS. Never use "*" with credentials. */
        private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:5173"));
        /** Honour X-Forwarded-For when identifying clients (only behind a trusted proxy). */
        private boolean trustForwardedHeaders = false;
        private RateLimit rateLimit = new RateLimit();
    }

    @Getter
    @Setter
    public static class RateLimit {
        private boolean enabled = true;
        /** Uploads per client per window. */
        private int uploadsPerWindow = 10;
        /** Transform / module-edit requests per client per window. */
        private int expensivePerWindow = 30;
        /** Downloads per client per window. */
        private int downloadsPerWindow = 60;
        /** Any other API request per client per window. */
        private int generalPerWindow = 600;
        private Duration window = Duration.ofMinutes(1);
    }

    @Getter
    @Setter
    public static class Jobs {
        /** Worker threads executing analysis/transformation jobs. */
        private int workerThreads = 2;
        /** Jobs waiting for a worker before new submissions are rejected. */
        private int maxQueuedJobs = 20;
        /** Concurrent (queued + running) jobs a single client may own. */
        private int maxActiveJobsPerClient = 3;
    }
}
