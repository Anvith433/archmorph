package com.anvith.archmorph.support;

import com.anvith.archmorph.analysis.transformation.TransformationEngine;
import com.anvith.archmorph.analysis.transformation.TransformationResult;
import com.anvith.archmorph.analysis.transformation.planner.TransformationPlan;
import com.anvith.archmorph.analysis.transformation.planner.TransformationPlanner;
import com.anvith.archmorph.analysis.transformation.target.TargetStrategy;
import com.anvith.archmorph.analysis.validation.ValidationEngine;
import com.anvith.archmorph.analysis.validation.ValidationReport;
import com.anvith.archmorph.pipeline.AnalysisResult;
import com.anvith.archmorph.pipeline.Deadline;
import com.anvith.archmorph.pipeline.ProgressListener;
import com.anvith.archmorph.pipeline.ProjectAnalyzer;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;

/** Runs the complete pipeline on a fixture copy: analyse → plan → transform → validate. */
@Component
public class PipelineRunner {

    private final ProjectAnalyzer analyzer;
    private final TransformationPlanner planner;
    private final TransformationEngine engine;
    private final ValidationEngine validation;

    public PipelineRunner(ProjectAnalyzer analyzer, TransformationPlanner planner, TransformationEngine engine,
                          ValidationEngine validation) {
        this.analyzer = analyzer;
        this.planner = planner;
        this.engine = engine;
        this.validation = validation;
    }

    public record Run(Path original, Path transformed, AnalysisResult analysis, TransformationPlan plan,
                      TransformationResult result, ValidationReport validation) {
    }

    public AnalysisResult analyze(String fixture, Path temp) throws IOException {
        Path original = Fixtures.copyTo(fixture, temp.resolve("original"));
        return analyzer.analyze(original, ProgressListener.NONE, Deadline.never());
    }

    public Run run(String fixture, Path temp) throws IOException {
        Path original = Fixtures.copyTo(fixture, temp.resolve("original"));
        AnalysisResult analysis = analyzer.analyze(original, ProgressListener.NONE, Deadline.never());
        TransformationPlan plan = planner.plan(analysis.model(), analysis.graph(), analysis.suggestion(),
                TargetStrategy.MODULAR_BY_DOMAIN);
        Path transformed = temp.resolve("transformed");
        TransformationResult result = engine.execute(analysis.model(), plan, transformed);
        ValidationReport report = validation.validate(analysis.model(), analysis.graph(), plan, analysis.suggestion(),
                transformed, temp.resolve("scratch"), ProgressListener.NONE, Deadline.never());
        return new Run(original, transformed, analysis, plan, result, report);
    }
}
