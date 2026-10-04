package com.anvith.archmorph.report;

import com.anvith.archmorph.api.dto.AnalysisDtos;
import com.anvith.archmorph.api.dto.ModuleDtos;
import com.anvith.archmorph.api.dto.PlanDtos;
import com.anvith.archmorph.api.dto.ValidationDtos;
import com.anvith.archmorph.api.mapper.ApiMapper;
import com.anvith.archmorph.common.exception.ArchMorphException;
import com.anvith.archmorph.common.exception.ErrorCode;
import com.anvith.archmorph.project.ProjectSession;
import com.anvith.archmorph.workspace.ProjectWorkspace;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Writes the machine-readable (JSON) and human-readable (Markdown) reports into the project's
 * {@code reports/} directory. File names come from {@link ReportName} only.
 */
@Service
public class ReportService {

    private final ApiMapper mapper;
    private final MarkdownReports markdown;
    private final JsonMapper json;

    public ReportService(ApiMapper mapper, MarkdownReports markdown, JsonMapper json) {
        this.mapper = mapper;
        this.markdown = markdown;
        this.json = json;
    }

    public void writeAnalysis(ProjectSession session) {
        AnalysisDtos.AnalysisDto analysis = mapper.analysis(session.analysis(), session.finalModules(), session.plan());
        ModuleDtos.ModulesDto modules = mapper.modules(session);
        write(session.workspace(), ReportName.ANALYSIS_JSON, json(analysis));
        write(session.workspace(), ReportName.MODULES_JSON, json(modules));
        write(session.workspace(), ReportName.ANALYSIS_MD, markdown.analysis(session.displayName(), analysis, modules.finalModules()));
    }

    public void writePlan(ProjectSession session) {
        PlanDtos.PlanDto plan = mapper.plan(session.plan(), session.finalModules());
        write(session.workspace(), ReportName.PLAN_JSON, json(plan));
        write(session.workspace(), ReportName.SUMMARY_MD, markdown.summary(session.displayName(), plan));
        write(session.workspace(), ReportName.MODULES_JSON, json(mapper.modules(session)));
    }

    public void writeValidation(ProjectSession session) {
        ValidationDtos.ValidationDto validation = mapper.validation(session.validation());
        write(session.workspace(), ReportName.VALIDATION_JSON, json(validation));
        write(session.workspace(), ReportName.VALIDATION_MD, markdown.validation(session.displayName(), validation));
    }

    /** Remove reports that no longer match the current plan (after module edits). */
    public void deleteTransformationReports(ProjectWorkspace workspace) {
        for (ReportName name : new ReportName[]{ReportName.VALIDATION_JSON, ReportName.VALIDATION_MD}) {
            try {
                Files.deleteIfExists(workspace.reports().resolve(name.fileName()));
            } catch (IOException ignored) {
                // best effort
            }
        }
    }

    public Optional<Path> find(ProjectWorkspace workspace, ReportName name) {
        Path path = workspace.reports().resolve(name.fileName());
        return Files.isRegularFile(path) ? Optional.of(path) : Optional.empty();
    }

    private String json(Object value) {
        return json.writerWithDefaultPrettyPrinter().writeValueAsString(value);
    }

    private void write(ProjectWorkspace workspace, ReportName name, String content) {
        try {
            Files.createDirectories(workspace.reports());
            Files.writeString(workspace.reports().resolve(name.fileName()), content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ArchMorphException(ErrorCode.STORAGE_ERROR, "A report could not be written.", null, e);
        }
    }
}
