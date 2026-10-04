package com.anvith.archmorph.cli;

import com.anvith.archmorph.analysis.transformation.SafetyLevel;
import com.anvith.archmorph.analysis.transformation.planner.TransformationPlan;
import com.anvith.archmorph.analysis.transformation.planner.TransformationPlanEntry;
import com.anvith.archmorph.analysis.validation.LevelResult;
import com.anvith.archmorph.analysis.validation.ValidationReport;
import com.anvith.archmorph.analysis.validation.ValidationStatus;
import com.anvith.archmorph.common.config.ArchMorphProperties;
import com.anvith.archmorph.common.exception.ArchMorphException;
import com.anvith.archmorph.common.util.FilenameSanitizer;
import com.anvith.archmorph.common.util.ZipWriter;
import com.anvith.archmorph.pipeline.ProgressListener;
import com.anvith.archmorph.project.ProjectSession;
import com.anvith.archmorph.project.ProjectWorkflow;
import com.anvith.archmorph.report.ReportName;
import com.anvith.archmorph.report.ReportService;
import com.anvith.archmorph.workspace.ProjectWorkspace;
import com.anvith.archmorph.workspace.WorkspaceManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;

/**
 * Command-line interface backed by the same {@link ProjectWorkflow} as the web API.
 *
 * <pre>
 * archmorph analyze   project.zip [--report-dir DIR]
 * archmorph plan      project.zip [--report-dir DIR]
 * archmorph transform project.zip --output transformed.zip [--report-dir DIR] [--no-build]
 * </pre>
 * Exit codes: 0 success, 1 failure, 2 usage error, 3 transformed but validation failed.
 */
@Component
@ConditionalOnProperty(name = "archmorph.cli.enabled", havingValue = "true")
public class CliRunner implements ApplicationRunner, ExitCodeGenerator {

    private final ProjectWorkflow workflow;
    private final WorkspaceManager workspaceManager;
    private final ReportService reports;
    private final ArchMorphProperties properties;
    private final PrintStream out;
    private int exitCode;

    @Autowired
    public CliRunner(ProjectWorkflow workflow, WorkspaceManager workspaceManager, ReportService reports,
                     ArchMorphProperties properties) {
        this(workflow, workspaceManager, reports, properties, System.out);
    }

    CliRunner(ProjectWorkflow workflow, WorkspaceManager workspaceManager, ReportService reports,
              ArchMorphProperties properties, OutputStream out) {
        this.workflow = workflow;
        this.workspaceManager = workspaceManager;
        this.reports = reports;
        this.properties = properties;
        this.out = out instanceof PrintStream ps ? ps : new PrintStream(out, true);
    }

    @Override
    public void run(ApplicationArguments arguments) {
        exitCode = execute(arguments.getSourceArgs());
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }

    /** Parses the arguments and runs one command; returns the process exit code. */
    int execute(String[] args) {
        if (args.length == 0 || args[0].equals("help") || args[0].equals("--help")) {
            usage();
            return args.length == 0 ? 2 : 0;
        }
        String command = args[0];
        Path zip = null;
        Path output = null;
        Path reportDir = null;
        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--output" -> output = i + 1 < args.length ? Path.of(args[++i]) : null;
                case "--report-dir" -> reportDir = i + 1 < args.length ? Path.of(args[++i]) : null;
                case "--no-build" -> properties.getValidation().getBuild().setEnabled(false);
                default -> {
                    if (args[i].startsWith("--")) {
                        out.println("Unknown option: " + args[i]);
                        usage();
                        return 2;
                    }
                    zip = Path.of(args[i]);
                }
            }
        }
        if (!List.of("analyze", "plan", "transform").contains(command) || zip == null) {
            usage();
            return 2;
        }
        if (command.equals("transform") && output == null) {
            out.println("transform requires --output <file.zip>");
            return 2;
        }
        if (!Files.isRegularFile(zip)) {
            out.println("File not found: " + zip.getFileName());
            return 2;
        }

        ProjectWorkspace workspace = workspaceManager.create();
        try {
            return run(command, zip, output, reportDir, workspace);
        } catch (ArchMorphException e) {
            out.println("Failed: " + e.getMessage());
            if (e.getHint() != null) {
                out.println("What you can do: " + e.getHint());
            }
            return 1;
        } catch (IOException | RuntimeException e) {
            out.println("Failed unexpectedly (" + e.getClass().getSimpleName() + ").");
            return 1;
        } finally {
            workspaceManager.delete(workspace.projectId());
        }
    }

    private int run(String command, Path zip, Path output, Path reportDir, ProjectWorkspace workspace) throws IOException {
        Files.copy(zip, workspace.archive(), StandardCopyOption.REPLACE_EXISTING);
        ProjectSession session = new ProjectSession(workspace, FilenameSanitizer.displayName(zip.getFileName().toString()),
                "cli", Files.size(zip));
        ProgressListener progress = (event, detail) -> out.println("  [" + event + "] " + detail);

        out.println("Analysing " + session.displayName() + " ...");
        workflow.analyze(session, progress);
        printAnalysis(session);

        int code = 0;
        if (command.equals("plan") || command.equals("transform")) {
            printPlan(session.plan());
        }
        if (command.equals("transform")) {
            out.println("Transforming ...");
            workflow.transform(session, progress);
            try (OutputStream stream = Files.newOutputStream(output)) {
                ZipWriter.write(workspace.transformed(), stream);
            }
            out.println("Transformed project written to " + output.getFileName());
            ValidationReport validation = session.validation();
            printValidation(validation);
            if (validation.status() == ValidationStatus.FAIL) {
                code = 3;
            }
        }
        if (reportDir != null) {
            copyReports(workspace, reportDir);
        }
        return code;
    }

    private void printAnalysis(ProjectSession session) {
        var architecture = session.analysis().architecture();
        out.println();
        out.printf("Classes %d | dependencies %d | controllers %d | services %d | repositories %d | entities %d%n",
                architecture.getClassCount(), architecture.getDependencyCount(), architecture.getControllerCount(),
                architecture.getServiceCount(), architecture.getRepositoryCount(), architecture.getEntityCount());
        out.printf("Cycles %d | layer violations %d | unclassified %d%n", architecture.getCycleCount(),
                architecture.getViolationCount(), architecture.getUnknownCount());
        out.println("Candidate modules (static-analysis indicators):");
        session.finalModules().getModules().forEach(m -> out.printf("  %-14s %-16s classes=%-3d confidence=%.2f cohesion=%.2f coupling=%.2f%n",
                m.getModuleName(), m.getCategory(), m.getClassCount(), m.getConfidence(), m.getCohesion(), m.getExternalCoupling()));
    }

    private void printPlan(TransformationPlan plan) {
        out.println();
        out.println("Plan: " + plan.movedCount() + " file(s) to move, " + plan.getConflicts().size() + " conflict(s)");
        for (TransformationPlanEntry e : plan.getEntries()) {
            if (e.getSafety() != SafetyLevel.SAFE || e.isMoved()) {
                out.printf("  %-18s %s -> %s  [%s]%n", e.getSafety(), e.getSourcePackage(), e.getTargetPackage(), e.getSourceFile().getFileName());
            }
        }
        plan.getWarnings().forEach(w -> out.println("  warning: " + w));
    }

    private void printValidation(ValidationReport report) {
        out.println();
        out.println("Validation: " + report.status());
        for (LevelResult level : report.levels()) {
            out.printf("  %-22s %-8s %s%n", level.level().getLabel(), level.status(), level.summary());
        }
    }

    private void copyReports(ProjectWorkspace workspace, Path reportDir) throws IOException {
        Files.createDirectories(reportDir);
        for (ReportName name : ReportName.values()) {
            Optional<Path> source = reports.find(workspace, name);
            if (source.isPresent()) {
                Files.copy(source.get(), reportDir.resolve(name.fileName()), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        out.println("Reports written to " + reportDir.getFileName());
    }

    private void usage() {
        out.println("""
                ArchMorph - static-analysis driven migration of layered Java applications to modular monoliths

                Usage:
                  archmorph analyze   <project.zip> [--report-dir DIR]
                  archmorph plan      <project.zip> [--report-dir DIR]
                  archmorph transform <project.zip> --output <transformed.zip> [--report-dir DIR] [--no-build]

                The original archive is never modified. See docs/API.md and README.md.""");
    }

    /** True when the arguments select CLI mode. */
    public static boolean isCliInvocation(String[] args) {
        return args.length > 0 && List.of("analyze", "plan", "transform", "help").contains(args[0]);
    }
}
