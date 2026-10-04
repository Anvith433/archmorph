package com.anvith.archmorph.analysis.validation;

import com.anvith.archmorph.analysis.dependency.DependencyGraph;
import com.anvith.archmorph.analysis.dependency.DependencyGraphBuilder;
import com.anvith.archmorph.analysis.model.ProjectModel;
import com.anvith.archmorph.analysis.model.ProjectModelBuilder;
import com.anvith.archmorph.analysis.module.ModuleDiscoveryReport;
import com.anvith.archmorph.analysis.transformation.planner.TransformationPlan;
import com.anvith.archmorph.analysis.validation.level.BuildValidator;
import com.anvith.archmorph.common.config.ArchMorphProperties;
import com.anvith.archmorph.parser.ProjectStructure;
import com.anvith.archmorph.parser.ProjectStructureDetector;
import com.anvith.archmorph.pipeline.Deadline;
import com.anvith.archmorph.pipeline.ProgressEvent;
import com.anvith.archmorph.pipeline.ProgressListener;
import com.anvith.archmorph.workspace.WorkspaceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Runs the seven validation levels against a transformed project: filesystem, Java parsing, package
 * consistency, import resolution, dependency graph, architecture rules and (when safely possible) a
 * sandboxed Maven build. Levels 1–6 only read and parse files; no project code is executed.
 */
@Service
public class ValidationEngine {

    private static final Logger log = LoggerFactory.getLogger(ValidationEngine.class);

    private final List<LevelValidator> validators;
    private final BuildValidator buildValidator;
    private final ProjectStructureDetector structureDetector;
    private final ProjectModelBuilder modelBuilder;
    private final DependencyGraphBuilder graphBuilder;
    private final ArchMorphProperties properties;
    private final WorkspaceManager workspaceManager;

    public ValidationEngine(List<LevelValidator> validators, BuildValidator buildValidator,
                            ProjectStructureDetector structureDetector, ProjectModelBuilder modelBuilder,
                            DependencyGraphBuilder graphBuilder, ArchMorphProperties properties,
                            WorkspaceManager workspaceManager) {
        this.validators = validators.stream()
                .sorted(java.util.Comparator.comparingInt(v -> v.level().ordinal())).toList();
        this.buildValidator = buildValidator;
        this.structureDetector = structureDetector;
        this.modelBuilder = modelBuilder;
        this.graphBuilder = graphBuilder;
        this.properties = properties;
        this.workspaceManager = workspaceManager;
    }

    public ValidationReport validate(ProjectModel original, DependencyGraph originalGraph, TransformationPlan plan,
                                     ModuleDiscoveryReport modules, Path transformedRoot, Path scratchParent,
                                     ProgressListener progress, Deadline deadline) {
        progress.onEvent(ProgressEvent.VALIDATION_STARTED, "Validating the transformed project");

        Supplier<ValidationContext.TransformedProject> transformed = memoize(() -> {
            ProjectStructure structure = structureDetector.detect(transformedRoot);
            ProjectModel model = modelBuilder.build(structure, Integer.MAX_VALUE, false);
            DependencyGraph graph = graphBuilder.build(model, Set.of(), false).graph();
            return new ValidationContext.TransformedProject(model, graph);
        });

        Path scratch;
        try {
            Files.createDirectories(scratchParent);
            scratch = Files.createTempDirectory(scratchParent, "validation-");
        } catch (IOException e) {
            throw new com.anvith.archmorph.common.exception.WorkspaceCreationException("Unable to prepare validation scratch space.", e);
        }
        ValidationContext context = new ValidationContext(original, originalGraph, plan, modules, transformedRoot,
                scratch, transformed);

        List<LevelResult> results = new ArrayList<>();
        for (LevelValidator validator : validators) {
            deadline.check();
            results.add(runSafely(validator, context));
        }

        BuildResult build = null;
        deadline.check();
        boolean structuralFailure = results.stream().anyMatch(r -> r.level() == ValidationLevel.JAVA_PARSING
                && r.status() == ValidationStatus.FAIL);
        if (structuralFailure) {
            results.add(LevelResult.skipped(ValidationLevel.BUILD, "Not run: the transformed sources do not parse."));
            WorkspaceManager.deleteRecursively(scratch);
        } else {
            Path repository = repository();
            BuildValidator.Outcome outcome = buildValidator.validate(context, repository);
            results.add(outcome.level());
            build = outcome.build();
        }

        ValidationStatus status = ValidationReport.overall(results);
        progress.onEvent(ProgressEvent.VALIDATION_COMPLETE, "Validation finished: " + status);
        log.info("Validation finished with status {}", status);
        return new ValidationReport(status, Instant.now(), List.copyOf(results), build, false);
    }

    private LevelResult runSafely(LevelValidator validator, ValidationContext context) {
        try {
            return validator.validate(context);
        } catch (RuntimeException e) {
            log.warn("Validation level {} failed internally: {}", validator.level(), e.getClass().getSimpleName());
            return new LevelResult(validator.level(), ValidationStatus.FAIL,
                    "The validation level could not complete.", 1,
                    List.of(ValidationIssue.error(null, 0, "Internal validation error.", "Please report this problem.")), 0);
        }
    }

    private Path repository() {
        String configured = properties.getValidation().getBuild().getLocalRepository();
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured).toAbsolutePath().normalize();
        }
        return workspaceManager.getProjectsRoot().getParent().resolve("maven-repository");
    }

    private static <T> Supplier<T> memoize(Supplier<T> delegate) {
        return new Supplier<>() {
            private T value;

            @Override
            public synchronized T get() {
                if (value == null) {
                    value = delegate.get();
                }
                return value;
            }
        };
    }
}
