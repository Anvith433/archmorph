package com.anvith.archmorph.report;

import com.anvith.archmorph.api.dto.AnalysisDtos;
import com.anvith.archmorph.api.dto.ModuleDtos;
import com.anvith.archmorph.api.dto.PlanDtos;
import com.anvith.archmorph.api.dto.ValidationDtos;
import org.springframework.stereotype.Component;

import java.util.List;

/** Human-readable Markdown renderings of the JSON reports. Values are escaped; no raw HTML is produced. */
@Component
public class MarkdownReports {

    public String analysis(String projectName, AnalysisDtos.AnalysisDto a, List<ModuleDtos.ModuleDto> modules) {
        StringBuilder md = new StringBuilder();
        md.append("# ArchMorph analysis: ").append(text(projectName)).append("\n\n");
        md.append("> ").append(com.anvith.archmorph.api.mapper.ApiMapper.INDICATOR_NOTE).append("\n\n");

        AnalysisDtos.Counts c = a.counts();
        md.append("## Overview\n\n| Metric | Value |\n|---|---|\n");
        row(md, "Java classes", c.classes());
        row(md, "Dependencies", c.dependencies());
        row(md, "Controllers", c.controllers());
        row(md, "Services", c.services());
        row(md, "Repositories", c.repositories());
        row(md, "Entities", c.entities());
        row(md, "DTOs", c.dtos());
        row(md, "Configuration classes", c.configurations());
        row(md, "Unclassified classes", c.unknown());
        row(md, "Dependency cycles", c.cycles());
        row(md, "Layer violations", c.layerViolations());
        row(md, "Candidate business modules", c.businessModules());
        md.append("\n**Indicators:** architecture health ").append(a.indicators().architectureHealth())
                .append("/100, module confidence ").append(a.indicators().moduleConfidence())
                .append("/100, transformation readiness ").append(a.indicators().transformationReadiness()).append("/100.\n\n");

        md.append("## Candidate modules\n\n| Module | Confidence | Classes | Cohesion | External coupling |\n|---|---|---|---|---|\n");
        for (ModuleDtos.ModuleDto m : modules) {
            if (m.category().equals("BUSINESS_MODULE")) {
                md.append("| ").append(text(m.name())).append(" | ").append(m.confidence()).append(" | ").append(m.classCount())
                        .append(" | ").append(m.cohesion()).append(" | ").append(m.externalCoupling()).append(" |\n");
            }
        }

        md.append("\n## Layer violations\n\n");
        if (a.violations().isEmpty()) {
            md.append("None detected.\n");
        }
        a.violations().stream().limit(50).forEach(v -> md.append("- ").append(text(v.message()))
                .append(v.file() == null ? "" : " (`" + code(v.file()) + ":" + v.line() + "`)").append("\n"));

        md.append("\n## Dependency cycles\n\n");
        if (a.cycles().isEmpty()) {
            md.append("None detected.\n");
        }
        a.cycles().forEach(cy -> md.append("- **").append(cy.cycleId()).append("** (").append(cy.severity()).append("): ")
                .append(text(String.join(" → ", cy.path()))).append("\n  - ").append(text(cy.recommendation())).append("\n"));

        if (!a.parseProblems().isEmpty()) {
            md.append("\n## Files that could not be parsed\n\n");
            a.parseProblems().forEach(p -> md.append("- `").append(code(p.file())).append("` line ").append(p.line()).append(": ")
                    .append(text(p.message())).append("\n"));
        }
        return md.toString();
    }

    public String summary(String projectName, PlanDtos.PlanDto plan) {
        StringBuilder md = new StringBuilder();
        md.append("# ArchMorph transformation summary: ").append(text(projectName)).append("\n\n");
        PlanDtos.PlanSummaryDto s = plan.summary();
        md.append("Strategy `").append(plan.strategy()).append("`, base package `").append(code(plan.basePackage())).append("`.\n\n");
        md.append("| | |\n|---|---|\n");
        row(md, "Files in plan", s.files());
        row(md, "Moved", s.moved());
        row(md, "Kept in place", s.kept());
        row(md, "Excluded by user", s.excluded());
        row(md, "Manual review required", s.manualReview());
        row(md, "Unsupported", s.unsupported());
        row(md, "Conflicts", s.conflicts());

        md.append("\n## Target layout\n\n```\n");
        plan.layout().forEach(l -> md.append(l).append("\n"));
        md.append("```\n\n## Changes\n\n| Class | From | To | Module | Actions | Safety |\n|---|---|---|---|---|---|\n");
        for (PlanDtos.PlanEntryDto e : plan.entries()) {
            md.append("| ").append(text(e.className())).append(" | `").append(code(e.sourcePackage())).append("` | `")
                    .append(code(e.targetPackage())).append("` | ").append(text(e.module() == null ? "-" : e.module()))
                    .append(" | ").append(String.join(", ", e.actions())).append(" | ").append(e.safety()).append(" |\n");
        }
        if (!plan.conflicts().isEmpty()) {
            md.append("\n## Conflicts\n\n");
            plan.conflicts().forEach(c -> md.append("- **").append(c.type()).append("** `").append(code(c.target())).append("`: ")
                    .append(text(c.resolution())).append("\n"));
        }
        if (!plan.warnings().isEmpty()) {
            md.append("\n## Warnings\n\n");
            plan.warnings().forEach(w -> md.append("- ").append(text(w)).append("\n"));
        }
        if (!plan.resourceFindings().isEmpty()) {
            md.append("\n## Configuration files that mention moved packages (not modified)\n\n");
            plan.resourceFindings().forEach(f -> md.append("- `").append(code(f.file())).append(":").append(f.line())
                    .append("` mentions `").append(code(f.reference())).append("`\n"));
        }
        md.append("\n## Guarantees\n\nArchMorph keeps your original project untouched, represents every move in this plan, and re-parses and "
                + "validates the generated sources. It does **not** guarantee that the application behaves identically.\n");
        return md.toString();
    }

    public String validation(String projectName, ValidationDtos.ValidationDto v) {
        StringBuilder md = new StringBuilder();
        md.append("# ArchMorph validation report: ").append(text(projectName)).append("\n\nOverall status: **")
                .append(v.status()).append("**\n\n| Level | Status | Summary |\n|---|---|---|\n");
        v.levels().forEach(l -> md.append("| ").append(l.label()).append(" | ").append(l.status()).append(" | ")
                .append(text(l.summary())).append(" |\n"));
        for (ValidationDtos.LevelDto l : v.levels()) {
            if (l.issues().isEmpty()) {
                continue;
            }
            md.append("\n## ").append(l.label()).append("\n\n");
            l.issues().stream().limit(50).forEach(i -> md.append("- ").append(i.severity()).append(": ").append(text(i.message()))
                    .append(i.file() == null ? "" : " (`" + code(i.file()) + (i.line() > 0 ? ":" + i.line() : "") + "`)")
                    .append(i.probableCause() == null ? "" : "\n  - Probable cause: " + text(i.probableCause())).append("\n"));
        }
        return md.toString();
    }

    private static void row(StringBuilder md, String label, Object value) {
        md.append("| ").append(label).append(" | ").append(value).append(" |\n");
    }

    /** Escape Markdown and HTML control characters in untrusted text. */
    static String text(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("`", "'").replace("|", "\\|").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\r", " ").replace("\n", " ");
    }

    static String code(String value) {
        return value == null ? "" : value.replace("`", "'").replace("\n", " ").replace("\r", " ");
    }
}
