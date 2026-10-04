package com.anvith.archmorph.project;

import com.anvith.archmorph.analysis.model.SourceFile;
import com.anvith.archmorph.analysis.module.ModuleDiscoveryReport;
import com.anvith.archmorph.analysis.module.editing.ModuleEdit;
import com.anvith.archmorph.analysis.module.editing.ModuleEditService;
import com.anvith.archmorph.analysis.transformation.DiffService;
import com.anvith.archmorph.analysis.transformation.FileTransformation;
import com.anvith.archmorph.analysis.transformation.TransformationEngine;
import com.anvith.archmorph.analysis.transformation.TransformationResult;
import com.anvith.archmorph.analysis.transformation.planner.TransformationPlan;
import com.anvith.archmorph.analysis.transformation.planner.TransformationPlanEntry;
import com.anvith.archmorph.analysis.transformation.planner.TransformationPlanner;
import com.anvith.archmorph.analysis.transformation.rewrite.RewriteResult;
import com.anvith.archmorph.analysis.transformation.rewrite.SourceRewriter;
import com.anvith.archmorph.analysis.transformation.target.TargetStrategy;
import com.anvith.archmorph.analysis.validation.ValidationEngine;
import com.anvith.archmorph.analysis.validation.ValidationReport;
import com.anvith.archmorph.common.config.ArchMorphProperties;
import com.anvith.archmorph.common.exception.ArchMorphException;
import com.anvith.archmorph.common.exception.ErrorCode;
import com.anvith.archmorph.common.exception.InvalidStateException;
import com.anvith.archmorph.common.exception.NotFoundException;
import com.anvith.archmorph.pipeline.AnalysisResult;
import com.anvith.archmorph.pipeline.Deadline;
import com.anvith.archmorph.pipeline.ProgressEvent;
import com.anvith.archmorph.pipeline.ProgressListener;
import com.anvith.archmorph.pipeline.ProjectAnalyzer;
import com.anvith.archmorph.report.ReportService;
import com.anvith.archmorph.upload.service.ExtractionReport;
import com.anvith.archmorph.upload.service.ZipExtractionService;
import com.anvith.archmorph.workspace.WorkspaceManager;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The pipeline behind both the REST API and the CLI:
 * extract → analyse → plan → (review) → transform → validate → report.
 *
 * <p>Each phase produces structured data held in the {@link ProjectSession}. The uploaded
 * original is never modified; the transformed project is generated into its own directory.</p>
 */
@Service
public class ProjectWorkflow {

    private static final int MAX_DIFF_CHARS = 400_000;

    private final ZipExtractionService extractor;
    private final ProjectAnalyzer analyzer;
    private final ModuleEditService editService;
    private final TransformationPlanner planner;
    private final TransformationEngine engine;
    private final ValidationEngine validationEngine;
    private final ReportService reports;
    private final SourceRewriter rewriter;
    private final DiffService diffService;
    private final com.anvith.archmorph.analysis.transformation.target.TargetArchitectureResolver architectures;
    private final com.anvith.archmorph.report.ModuleDocumentation moduleDocumentation;
    private final com.anvith.archmorph.analysis.module.boundary.BoundaryAdvisor boundaryAdvisor;
    private final ArchMorphProperties properties;

    public ProjectWorkflow(ZipExtractionService extractor, ProjectAnalyzer analyzer, ModuleEditService editService,
                           TransformationPlanner planner, TransformationEngine engine, ValidationEngine validationEngine,
                           ReportService reports, SourceRewriter rewriter, DiffService diffService,
                           com.anvith.archmorph.analysis.transformation.target.TargetArchitectureResolver architectures,
                           com.anvith.archmorph.report.ModuleDocumentation moduleDocumentation,
                           com.anvith.archmorph.analysis.module.boundary.BoundaryAdvisor boundaryAdvisor,
                           ArchMorphProperties properties) {
        this.extractor = extractor;
        this.analyzer = analyzer;
        this.editService = editService;
        this.planner = planner;
        this.engine = engine;
        this.validationEngine = validationEngine;
        this.reports = reports;
        this.rewriter = rewriter;
        this.diffService = diffService;
        this.architectures = architectures;
        this.moduleDocumentation = moduleDocumentation;
        this.boundaryAdvisor = boundaryAdvisor;
        this.properties = properties;
    }

    // ================================================================== analysis

    /** Extract the uploaded archive, analyse it and create the initial module suggestion and plan. */
    public void analyze(ProjectSession session, ProgressListener progress) {
        Deadline deadline = Deadline.after(properties.getAnalysis().getTimeout(), "analysis");
        try {
            session.setStatus(ProjectStatus.ANALYZING);
            session.clearFailure();
            WorkspaceManager.resetDirectory(session.workspace().original());
            ExtractionReport extraction = extractor.extract(session.workspace().archive(), session.workspace().original());
            if (extraction.extractedFiles() > properties.getAnalysis().getMaxProjectFiles()) {
                throw new ArchMorphException(ErrorCode.ANALYSIS_LIMIT_EXCEEDED,
                        "The archive contains more than " + properties.getAnalysis().getMaxProjectFiles() + " files.",
                        "Remove generated directories such as target/ and node_modules/ and upload again.");
            }
            progress.onEvent(ProgressEvent.EXTRACTION_COMPLETE, extraction.extractedFiles() + " files extracted"
                    + (extraction.skippedEntries() > 0 ? ", " + extraction.skippedEntries() + " generated/IDE entries skipped" : ""));
            deadline.check();

            AnalysisResult analysis = analyzer.analyze(session.workspace().original(), progress, deadline);

            session.setStatus(ProjectStatus.PLANNING);
            session.lock().lock();
            try {
                session.setAnalysis(analysis);
                session.setDecisions(List.of());
                rebuildPlan(session, progress);
                reports.writeAnalysis(session);
                reports.writePlan(session);
                session.setStatus(ProjectStatus.READY_FOR_REVIEW);
            } finally {
                session.lock().unlock();
            }
        } catch (ArchMorphException e) {
            session.fail(e.getErrorCode().name(), e.getMessage(), e.getHint());
            throw e;
        } catch (RuntimeException e) {
            session.fail(ErrorCode.INTERNAL_ERROR.name(), "The analysis failed unexpectedly.", null);
            throw e;
        }
    }

    // ================================================================== module decisions

    /**
     * Replace the user's module decisions and recompute the final modules and plan.
     * The previous state is kept if any decision is invalid. A completed transformation is invalidated.
     */
    public ModuleDiscoveryReport updateModules(ProjectSession session, List<ModuleEdit> edits) {
        ReentrantLock lock = session.lock();
        lock.lock();
        try {
            requireAnalysis(session);
            ModuleDiscoveryReport result = editService.apply(session.analysis().suggestion(), edits, session.analysis().graph());
            session.setDecisions(edits);
            session.setFinalModules(result);
            invalidateTransformation(session);
            session.setPlan(planner.plan(session.analysis().model(), session.analysis().graph(), result, strategyOf(session)));
            reports.writePlan(session);
            session.setStatus(ProjectStatus.READY_FOR_REVIEW);
            return result;
        } finally {
            lock.unlock();
        }
    }

    /** Choose the target layout and re-plan. A completed transformation is invalidated. */
    public TransformationPlan changeStrategy(ProjectSession session, TargetStrategy strategy) {
        ReentrantLock lock = session.lock();
        lock.lock();
        try {
            requireAnalysis(session);
            session.setStrategy(strategy);
            invalidateTransformation(session);
            ModuleDiscoveryReport modules = session.finalModules() != null ? session.finalModules() : session.analysis().suggestion();
            TransformationPlan plan = planner.plan(session.analysis().model(), session.analysis().graph(), modules, strategy);
            session.setPlan(plan);
            reports.writePlan(session);
            session.setStatus(ProjectStatus.READY_FOR_REVIEW);
            return plan;
        } finally {
            lock.unlock();
        }
    }

    /** MODULES.md in the transformed project; never overwrites a file the project already has. */
    private void writeModuleDocumentation(ProjectSession session) {
        Path root = session.workspace().transformed();
        Path target = root.resolve(com.anvith.archmorph.report.ModuleDocumentation.FILE_NAME);
        if (Files.exists(target)) {
            target = root.resolve(com.anvith.archmorph.report.ModuleDocumentation.FALLBACK_FILE_NAME);
            if (Files.exists(target)) {
                return;
            }
        }
        ModuleDiscoveryReport modules = session.finalModules() != null ? session.finalModules() : session.analysis().suggestion();
        String markdown = moduleDocumentation.render(session.displayName(), session.plan(), modules, session.analysis().graph(),
                architectures.resolve(session.plan().getStrategy()), boundaries(session));
        try {
            Files.writeString(target, markdown, java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new com.anvith.archmorph.common.exception.WorkspaceCreationException("Unable to write the module documentation.", e);
        }
    }

    /** Module cycles of the current module assignment and how to break them. */
    public com.anvith.archmorph.analysis.module.boundary.BoundaryReport boundaries(ProjectSession session) {
        ModuleDiscoveryReport modules = session.finalModules() != null ? session.finalModules() : session.analysis().suggestion();
        return boundaryAdvisor.advise(modules, session.analysis().graph(), session.analysis().facts());
    }

    public TargetStrategy strategyOf(ProjectSession session) {
        return session.strategy() != null ? session.strategy() : properties.getTransformation().getStrategy();
    }

    // ================================================================== dry run & diff

    /** Plan, rewrite everything in memory and report. Does not touch the transformed workspace. */
    public TransformationResult dryRun(ProjectSession session) {
        session.lock().lock();
        try {
            requireAnalysis(session);
            TransformationPlan plan = session.plan();
            return engine.dryRun(session.analysis().model(), plan);
        } finally {
            session.lock().unlock();
        }
    }

    public record DiffData(TransformationPlanEntry entry, String before, String after, String unified,
                           boolean truncated, RewriteResult rewrite) {
    }

    /** Before/after view of one plan entry, computed with the same deterministic rewriter the executor uses. */
    public DiffData diff(ProjectSession session, String entryId) {
        session.lock().lock();
        try {
            requireAnalysis(session);
            TransformationPlan plan = session.plan();
            TransformationPlanEntry entry = plan.getEntries().stream().filter(e -> e.getId().equals(entryId)).findFirst()
                    .orElseThrow(() -> new NotFoundException(ErrorCode.RESOURCE_NOT_FOUND, "Plan entry not found."));
            SourceFile file = session.analysis().model().files().stream()
                    .filter(f -> f.getRelativePath().equals(entry.getSourceFile().toString().replace('\\', '/')))
                    .findFirst().orElseThrow(() -> new NotFoundException(ErrorCode.RESOURCE_NOT_FOUND, "Source file not found."));

            String before = file.getParsed().source();
            String after = before;
            RewriteResult rewrite = null;
            if (file.isParseable()) {
                rewrite = rewriter.rewrite(before, entry.getTargetPackage(), plan.getClassMap(), session.analysis().model().registry());
                after = rewrite.source();
            }
            boolean truncated = before.length() > MAX_DIFF_CHARS || after.length() > MAX_DIFF_CHARS;
            String unified = truncated ? "" : diffService.unifiedDiff(entry.getSourceFile().toString().replace('\\', '/'),
                    entry.getTargetFile().toString().replace('\\', '/'), before, after);
            if (truncated) {
                before = before.substring(0, Math.min(before.length(), MAX_DIFF_CHARS));
                after = after.substring(0, Math.min(after.length(), MAX_DIFF_CHARS));
            }
            return new DiffData(entry, before, after, unified, truncated, rewrite);
        } finally {
            session.lock().unlock();
        }
    }

    // ================================================================== transform + validate

    /** Plan from the final modules, generate the transformed project, validate it and write reports. */
    public void transform(ProjectSession session, ProgressListener progress) {
        Deadline deadline = Deadline.after(properties.getTransformation().getTimeout(), "transformation");
        try {
            session.lock().lock();
            try {
                requireAnalysis(session);
                if (session.status() != ProjectStatus.READY_FOR_REVIEW && session.status() != ProjectStatus.COMPLETED
                        && session.status() != ProjectStatus.FAILED) {
                    throw new InvalidStateException("The project is not ready to be transformed.",
                            "Wait until the analysis has finished.");
                }
                session.setStatus(ProjectStatus.PLANNING);
                invalidateTransformation(session);
                rebuildPlan(session, progress);
                reports.writePlan(session);
                session.setStatus(ProjectStatus.TRANSFORMING);
            } finally {
                session.lock().unlock();
            }

            progress.onEvent(ProgressEvent.TRANSFORMATION_STARTED, "Generating the modular project");
            TransformationResult result = engine.execute(session.analysis().model(), session.plan(), session.workspace().transformed());
            writeModuleDocumentation(session);
            deadline.check();
            session.lock().lock();
            try {
                session.setTransformation(result);
                session.setTransformedAvailable(true);
            } finally {
                session.lock().unlock();
            }
            progress.onEvent(ProgressEvent.TRANSFORMATION_COMPLETE, result.javaFilesMoved() + " Java files moved");

            session.setStatus(ProjectStatus.VALIDATING);
            runValidation(session, progress, deadline);
            reports.writePlan(session);
            session.setStatus(ProjectStatus.COMPLETED);
        } catch (ArchMorphException e) {
            WorkspaceManager.deleteRecursively(session.workspace().transformed());
            session.setTransformedAvailable(false);
            session.fail(e.getErrorCode().name(), e.getMessage(), e.getHint());
            throw e;
        } catch (RuntimeException e) {
            WorkspaceManager.deleteRecursively(session.workspace().transformed());
            session.setTransformedAvailable(false);
            session.fail(ErrorCode.INTERNAL_ERROR.name(), "The transformation failed unexpectedly.", null);
            throw e;
        }
    }

    /** Re-run validation on the existing transformed project. */
    public void validate(ProjectSession session, ProgressListener progress) {
        Deadline deadline = Deadline.after(properties.getTransformation().getTimeout(), "validation");
        if (!session.transformedAvailable()) {
            throw new InvalidStateException("There is no transformed project to validate.", "Run the transformation first.");
        }
        ProjectStatus previous = session.status();
        try {
            session.setStatus(ProjectStatus.VALIDATING);
            runValidation(session, progress, deadline);
        } finally {
            session.setStatus(previous == ProjectStatus.VALIDATING ? ProjectStatus.COMPLETED : previous);
        }
    }

    private void runValidation(ProjectSession session, ProgressListener progress, Deadline deadline) {
        Path scratchParent = session.workspace().root().resolve("scratch");
        ValidationReport report = validationEngine.validate(session.analysis().model(), session.analysis().graph(),
                session.plan(), session.finalModules(), session.workspace().transformed(), scratchParent, progress, deadline);
        session.lock().lock();
        try {
            session.setValidation(report);
            reports.writeValidation(session);
        } finally {
            session.lock().unlock();
        }
    }

    // ================================================================== helpers

    private void rebuildPlan(ProjectSession session, ProgressListener progress) {
        AnalysisResult analysis = session.analysis();
        ModuleDiscoveryReport modules = editService.apply(analysis.suggestion(), session.decisions(), analysis.graph());
        session.setFinalModules(modules);
        TransformationPlan plan = planner.plan(analysis.model(), analysis.graph(), modules, strategyOf(session));
        session.setPlan(plan);
        progress.onEvent(ProgressEvent.PLAN_CREATED, plan.getEntries().size() + " files planned, " + plan.movedCount() + " to move");
    }

    private void invalidateTransformation(ProjectSession session) {
        session.invalidateTransformation();
        WorkspaceManager.deleteRecursively(session.workspace().transformed());
        reports.deleteTransformationReports(session.workspace());
    }

    private void requireAnalysis(ProjectSession session) {
        if (!session.analysed()) {
            throw new InvalidStateException("The project has not been analysed yet.",
                    "Wait for the analysis job to finish, or start a new analysis.");
        }
    }

    /** Summary used by tests and the CLI. */
    public List<FileTransformation> changedFiles(TransformationResult result) {
        return result.files().stream().filter(FileTransformation::changed).toList();
    }
}
