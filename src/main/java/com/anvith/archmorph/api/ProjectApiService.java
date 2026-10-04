package com.anvith.archmorph.api;

import com.anvith.archmorph.analysis.module.editing.ModuleEdit;
import com.anvith.archmorph.analysis.transformation.TransformationResult;
import com.anvith.archmorph.api.dto.AnalysisDtos;
import com.anvith.archmorph.api.dto.ArchitectureDtos;
import com.anvith.archmorph.api.dto.GraphDtos;
import com.anvith.archmorph.api.dto.ModuleDtos;
import com.anvith.archmorph.api.dto.PlanDtos;
import com.anvith.archmorph.api.dto.ProjectDtos;
import com.anvith.archmorph.api.dto.ValidationDtos;
import com.anvith.archmorph.api.mapper.ApiMapper;
import com.anvith.archmorph.common.config.ArchMorphProperties;
import com.anvith.archmorph.common.exception.ErrorCode;
import com.anvith.archmorph.common.exception.InvalidStateException;
import com.anvith.archmorph.common.exception.NotFoundException;
import com.anvith.archmorph.common.util.FilenameSanitizer;
import com.anvith.archmorph.job.Job;
import com.anvith.archmorph.job.JobManager;
import com.anvith.archmorph.job.JobStatus;
import com.anvith.archmorph.job.JobStore;
import com.anvith.archmorph.job.JobType;
import com.anvith.archmorph.pipeline.ProgressEvent;
import com.anvith.archmorph.pipeline.ProgressListener;
import com.anvith.archmorph.project.ProjectAccessPolicy;
import com.anvith.archmorph.project.ProjectSession;
import com.anvith.archmorph.project.ProjectSessionStore;
import com.anvith.archmorph.project.ProjectStatus;
import com.anvith.archmorph.project.ProjectWorkflow;
import com.anvith.archmorph.report.ReportName;
import com.anvith.archmorph.report.ReportService;
import com.anvith.archmorph.upload.service.ArchiveStorageService;
import com.anvith.archmorph.upload.service.ProjectValidator;
import com.anvith.archmorph.workspace.ProjectWorkspace;
import com.anvith.archmorph.workspace.WorkspaceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Application service behind the REST controllers: access control, state checks, job creation and
 * DTO mapping. Business logic lives in {@link ProjectWorkflow}.
 */
@Service
public class ProjectApiService {

    private static final Logger log = LoggerFactory.getLogger(ProjectApiService.class);
    private static final Pattern ENTRY_ID = Pattern.compile("^e-\\d{4,7}$");

    private final WorkspaceManager workspaceManager;
    private final ProjectValidator validator;
    private final ArchiveStorageService archiveStorage;
    private final ProjectSessionStore sessions;
    private final JobManager jobManager;
    private final JobStore jobStore;
    private final ProjectWorkflow workflow;
    private final ReportService reports;
    private final ApiMapper mapper;
    private final ProjectAccessPolicy accessPolicy;
    private final ArchMorphProperties properties;

    public ProjectApiService(WorkspaceManager workspaceManager, ProjectValidator validator, ArchiveStorageService archiveStorage,
                             ProjectSessionStore sessions, JobManager jobManager, JobStore jobStore, ProjectWorkflow workflow,
                             ReportService reports, ApiMapper mapper, ProjectAccessPolicy accessPolicy,
                             ArchMorphProperties properties) {
        this.workspaceManager = workspaceManager;
        this.validator = validator;
        this.archiveStorage = archiveStorage;
        this.sessions = sessions;
        this.jobManager = jobManager;
        this.jobStore = jobStore;
        this.workflow = workflow;
        this.reports = reports;
        this.mapper = mapper;
        this.accessPolicy = accessPolicy;
        this.properties = properties;
    }

    // ================================================================== lifecycle

    public ProjectDtos.CreatedProjectDto create(MultipartFile file, String clientId) {
        validator.validate(file);
        jobManager.assertCapacity(clientId);

        ProjectWorkspace workspace = workspaceManager.create();
        String displayName = FilenameSanitizer.displayName(file.getOriginalFilename());
        try {
            archiveStorage.saveArchive(workspace, file);
            ProjectSession session = new ProjectSession(workspace, displayName, clientId, file.getSize());
            sessions.put(session);
            Job job = startAnalysis(session, clientId, true);
            log.info("Project {} uploaded, job {} queued", workspace.projectId(), job.id());
            return new ProjectDtos.CreatedProjectDto(workspace.projectId(), job.id(), job.status().name());
        } catch (RuntimeException e) {
            sessions.remove(workspace.projectId());
            workspaceManager.delete(workspace.projectId());
            throw e;
        }
    }

    public ProjectDtos.CreatedProjectDto reanalyze(String projectId, String clientId) {
        ProjectSession session = require(projectId, clientId);
        jobManager.assertCapacity(clientId);
        Job job = startAnalysis(session, clientId, false);
        return new ProjectDtos.CreatedProjectDto(projectId, job.id(), job.status().name());
    }

    private Job startAnalysis(ProjectSession session, String clientId, boolean fresh) {
        session.setStatus(ProjectStatus.QUEUED);
        Job job = jobManager.submit(session.projectId(), JobType.ANALYZE, clientId, j -> {
            j.addEvent(ProgressEvent.UPLOAD_COMPLETE, fresh ? "Archive received" : "Re-analysis requested");
            j.transition(JobStatus.ANALYZING);
            workflow.analyze(session, listener(j));
        });
        session.setLatestJobId(job.id());
        return job;
    }

    public void delete(String projectId, String clientId) {
        ProjectSession session = require(projectId, clientId);
        sessions.remove(session.projectId());
        jobStore.removeForProject(session.projectId());
        workspaceManager.delete(session.projectId());
        log.info("Project {} deleted on request", projectId);
    }

    // ================================================================== reads

    public ProjectDtos.ProjectDto project(String projectId, String clientId) {
        ProjectSession session = require(projectId, clientId);
        Job latest = session.latestJobId() == null ? null : jobStore.find(session.latestJobId()).orElse(null);
        return mapper.project(session, latest);
    }

    public ProjectDtos.JobDto job(String jobId, String clientId) {
        Job job = jobManager.require(jobId);
        require(job.projectId(), clientId);
        return mapper.job(job);
    }

    public AnalysisDtos.AnalysisDto analysis(String projectId, String clientId) {
        ProjectSession session = analysed(projectId, clientId);
        return mapper.analysis(session.analysis(), session.finalModules(), session.plan());
    }

    public GraphDtos.GraphDto dependencies(String projectId, String clientId, int limit) {
        ProjectSession session = analysed(projectId, clientId);
        return mapper.graph(session.analysis(), session.finalModules(), Math.max(1, Math.min(limit, 20_000)));
    }

    public ArchitectureDtos.ArchitectureDto architecture(String projectId, String clientId) {
        ProjectSession session = analysed(projectId, clientId);
        return mapper.architecture(session.analysis(), session.finalModules(), session.plan());
    }

    public ModuleDtos.ModulesDto modules(String projectId, String clientId) {
        return mapper.modules(analysed(projectId, clientId));
    }

    public PlanDtos.PlanDto plan(String projectId, String clientId) {
        ProjectSession session = analysed(projectId, clientId);
        return mapper.plan(session.plan(), session.finalModules());
    }

    public ValidationDtos.ValidationDto validation(String projectId, String clientId) {
        ProjectSession session = require(projectId, clientId);
        if (session.validation() == null) {
            throw new InvalidStateException("The project has not been validated yet.", "Run the transformation first.");
        }
        return mapper.validation(session.validation());
    }

    public PlanDtos.DiffDto diff(String projectId, String entryId, String clientId) {
        ProjectSession session = analysed(projectId, clientId);
        if (entryId == null || !ENTRY_ID.matcher(entryId).matches()) {
            throw new NotFoundException(ErrorCode.RESOURCE_NOT_FOUND, "Plan entry not found.");
        }
        ProjectWorkflow.DiffData diff = workflow.diff(session, entryId);
        List<String> imports = diff.rewrite() == null ? List.of() : diff.rewrite().importChanges().stream()
                .map(c -> c.kind() + " " + (c.from() == null ? "" : c.from()) + (c.to() == null ? "" : " → " + c.to())).toList();
        var entry = diff.entry();
        return new PlanDtos.DiffDto(entry.getId(),
                entry.getNode() != null ? entry.getNode().getClassName() : entry.getSourceFile().getFileName().toString(),
                entry.getSourceFile().toString().replace('\\', '/'), entry.getTargetFile().toString().replace('\\', '/'),
                diff.before(), diff.after(), diff.unified(), !diff.before().equals(diff.after()), diff.truncated(),
                imports, diff.rewrite() == null ? 0 : diff.rewrite().qualifiedRewrites());
    }

    // ================================================================== actions

    public ModuleDtos.ModulesDto updateModules(String projectId, ModuleDtos.UpdateModulesRequest request, String clientId) {
        ProjectSession session = analysed(projectId, clientId);
        requireIdle(session);
        List<ModuleEdit> edits = request.edits().stream().map(mapper::edit).toList();
        workflow.updateModules(session, edits);
        return mapper.modules(session);
    }

    public PlanDtos.PlanDto changeStrategy(String projectId,
                                           com.anvith.archmorph.analysis.transformation.target.TargetStrategy strategy,
                                           String clientId) {
        ProjectSession session = analysed(projectId, clientId);
        requireIdle(session);
        workflow.changeStrategy(session, strategy);
        return mapper.plan(session.plan(), session.finalModules());
    }

    public PlanDtos.DryRunDto dryRun(String projectId, String clientId) {
        ProjectSession session = analysed(projectId, clientId);
        requireIdle(session);
        TransformationResult result = workflow.dryRun(session);
        return mapper.dryRun(session.plan(), session.finalModules(), result);
    }

    public ProjectDtos.CreatedProjectDto transform(String projectId, String clientId) {
        ProjectSession session = analysed(projectId, clientId);
        requireIdle(session);
        jobManager.assertCapacity(clientId);
        Job job = jobManager.submit(projectId, JobType.TRANSFORM, clientId, j -> {
            j.transition(JobStatus.PLANNING);
            workflow.transform(session, listener(j));
        });
        session.setLatestJobId(job.id());
        return new ProjectDtos.CreatedProjectDto(projectId, job.id(), job.status().name());
    }

    public ProjectDtos.CreatedProjectDto validate(String projectId, String clientId) {
        ProjectSession session = analysed(projectId, clientId);
        requireIdle(session);
        jobManager.assertCapacity(clientId);
        Job job = jobManager.submit(projectId, JobType.VALIDATE, clientId, j -> {
            j.transition(JobStatus.VALIDATING);
            workflow.validate(session, listener(j));
        });
        session.setLatestJobId(job.id());
        return new ProjectDtos.CreatedProjectDto(projectId, job.id(), job.status().name());
    }

    // ================================================================== downloads

    /** A validated, workspace-internal directory that may be streamed as a ZIP. */
    public record DownloadSource(Path directory, String fileName) {
    }

    public DownloadSource transformedProject(String projectId, String clientId) {
        ProjectSession session = require(projectId, clientId);
        boolean validated = !properties.getTransformation().isRequireValidation() || session.validation() != null;
        if (session.status() != ProjectStatus.COMPLETED || !session.transformedAvailable() || !validated) {
            throw new InvalidStateException("The transformed project is not available.",
                    "Run the transformation and wait for validation to finish before downloading.");
        }
        return new DownloadSource(session.workspace().transformed(),
                "archmorph-" + FilenameSanitizer.slug(session.displayName()) + "-transformed.zip");
    }

    public record ReportSource(Path file, ReportName name, String downloadName) {
    }

    public ReportSource report(String projectId, String reportFileName, String clientId) {
        ProjectSession session = require(projectId, clientId);
        ReportName name = ReportName.fromFileName(reportFileName)
                .orElseThrow(() -> new NotFoundException(ErrorCode.RESOURCE_NOT_FOUND, "Report not found."));
        Path file = reports.find(session.workspace(), name)
                .orElseThrow(() -> new NotFoundException(ErrorCode.RESOURCE_NOT_FOUND, "Report not available yet."));
        return new ReportSource(file, name, "archmorph-" + FilenameSanitizer.slug(session.displayName()) + "-" + name.fileName());
    }

    // ================================================================== helpers

    public ProjectSession require(String projectId, String clientId) {
        if (!WorkspaceManager.isValidProjectId(projectId)) {
            throw new NotFoundException(ErrorCode.PROJECT_NOT_FOUND, "Project not found.");
        }
        ProjectSession session = sessions.find(projectId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.PROJECT_NOT_FOUND, "Project not found."));
        accessPolicy.check(session, clientId);
        session.touch();
        return session;
    }

    private ProjectSession analysed(String projectId, String clientId) {
        ProjectSession session = require(projectId, clientId);
        if (!session.analysed()) {
            if (session.status() == ProjectStatus.FAILED) {
                throw new InvalidStateException("The analysis of this project failed.",
                        session.failureHint() == null ? "Upload the project again." : session.failureHint());
            }
            throw new InvalidStateException("The analysis is not finished yet.", "Poll the job until it is COMPLETED.");
        }
        return session;
    }

    private void requireIdle(ProjectSession session) {
        ProjectStatus status = session.status();
        if (status == ProjectStatus.QUEUED || status == ProjectStatus.ANALYZING || status == ProjectStatus.PLANNING
                || status == ProjectStatus.TRANSFORMING || status == ProjectStatus.VALIDATING) {
            throw new InvalidStateException("The project is busy (" + status + ").", "Wait for the running job to finish.");
        }
    }

    /** Translates pipeline events into job events and status transitions. */
    private ProgressListener listener(Job job) {
        return (event, detail) -> {
            job.addEvent(event, detail);
            switch (event) {
                case EXTRACTION_COMPLETE, STRUCTURE_DETECTED, PARSING_STARTED, PARSING_COMPLETE,
                     DEPENDENCY_ANALYSIS_COMPLETE, ARCHITECTURE_ANALYSIS_COMPLETE, MODULE_DISCOVERY_COMPLETE ->
                        job.transition(JobStatus.ANALYZING);
                case PLAN_CREATED -> job.transition(JobStatus.PLANNING);
                case TRANSFORMATION_STARTED, TRANSFORMATION_COMPLETE -> job.transition(JobStatus.TRANSFORMING);
                case VALIDATION_STARTED, VALIDATION_COMPLETE -> job.transition(JobStatus.VALIDATING);
                default -> {
                    // no status change
                }
            }
        };
    }
}
