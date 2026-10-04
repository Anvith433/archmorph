package com.anvith.archmorph;

import com.anvith.archmorph.analysis.module.ModuleInfo;
import com.anvith.archmorph.analysis.transformation.TransformationEngine;
import com.anvith.archmorph.analysis.transformation.TransformationResult;
import com.anvith.archmorph.analysis.transformation.planner.TransformationPlan;
import com.anvith.archmorph.analysis.transformation.planner.TransformationPlanEntry;
import com.anvith.archmorph.analysis.transformation.planner.TransformationPlanner;
import com.anvith.archmorph.analysis.transformation.target.TargetStrategy;
import com.anvith.archmorph.pipeline.AnalysisResult;
import com.anvith.archmorph.pipeline.Deadline;
import com.anvith.archmorph.pipeline.ProgressListener;
import com.anvith.archmorph.pipeline.ProjectAnalyzer;
import com.anvith.archmorph.support.Fixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;

@SpringBootTest(properties = {"archmorph.workspace.root=target/test-workspace",
        "archmorph.validation.build.offline=true"})
class ExploreTest {

    @Autowired ProjectAnalyzer analyzer;
    @Autowired TransformationPlanner planner;
    @Autowired TransformationEngine engine;
    @Autowired com.anvith.archmorph.analysis.validation.ValidationEngine validation;

    @TempDir Path temp;

    @Test
    void explore() throws Exception {
        String name = System.getProperty("fixture", "spring-layered");
        Path project = Fixtures.copyTo(name, temp.resolve("in"));
        AnalysisResult result = analyzer.analyze(project, ProgressListener.NONE, Deadline.never());
        System.out.println("=== MODULES");
        for (ModuleInfo m : result.suggestion().getModules()) {
            System.out.printf("%s [%s] conf=%.2f coh=%.2f coup=%.2f classes=%s warn=%s%n", m.getModuleName(), m.getCategory(),
                    m.getConfidence(), m.getCohesion(), m.getExternalCoupling(),
                    m.getClasses().stream().map(n -> n.getClassName()).toList(), m.getWarnings());
        }
        System.out.println("=== ARCH violations=" + result.architecture().getViolations() + " cycles=" + result.cycles().getCycleCount());
        TransformationPlan plan = planner.plan(result.model(), result.graph(), result.suggestion(), TargetStrategy.MODULAR_BY_DOMAIN);
        System.out.println("=== PLAN base=" + plan.getBasePackage());
        for (TransformationPlanEntry e : plan.getEntries()) {
            System.out.printf("%s %s -> %s %s %s %s%n", e.getScope(), e.getSourceFile(), e.getTargetFile(), e.getActions(), e.getSafety(), e.getReasons());
        }
        System.out.println("conflicts=" + plan.getConflicts() + " findings=" + plan.getResourceFindings());
        System.out.println("warnings=" + plan.getWarnings());
        Path out = temp.resolve("out");
        TransformationResult tr = engine.execute(result.model(), plan, out);
        var report = validation.validate(result.model(), result.graph(), plan, result.suggestion(), out, temp.resolve("scratch"),
                ProgressListener.NONE, Deadline.never());
        System.out.println("=== VALIDATION " + report.status());
        report.levels().forEach(l -> System.out.println("  " + l.level() + " " + l.status() + " - " + l.summary() + " " + l.issues()));
        if (report.build() != null) System.out.println("  build exit=" + report.build().exitCode() + " cmd=" + report.build().command() + "\n" + report.build().stdout() + report.build().stderr());
        System.out.println("=== RESULT moved=" + tr.javaFilesMoved() + " copied=" + tr.resourceFilesCopied());
        for (String f : new String[]{"src/main/java/com/demo/modules/order/service/OrderService.java", "src/main/java/com/demo/modules/payment/service/PaymentService.java"}) {
            Path p = out.resolve(f);
            if (Files.exists(p)) System.out.println("----- " + f + "\n" + Files.readString(p));
        }
    }
}
