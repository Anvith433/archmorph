package com.anvith.archmorph.api;

import com.anvith.archmorph.api.dto.AnalysisDtos;
import com.anvith.archmorph.api.dto.ArchitectureDtos;
import com.anvith.archmorph.api.dto.GraphDtos;
import com.anvith.archmorph.api.dto.ModuleDtos;
import com.anvith.archmorph.api.dto.PlanDtos;
import com.anvith.archmorph.api.dto.ProjectDtos;
import com.anvith.archmorph.api.dto.ValidationDtos;
import com.anvith.archmorph.api.web.ClientResolver;
import com.anvith.archmorph.common.response.ApiResponse;
import com.anvith.archmorph.common.util.ZipWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.file.Files;

/** Versioned REST API. Controllers only translate HTTP; behaviour lives in {@link ProjectApiService}. */
@RestController
@RequestMapping("/api/v1")
public class ProjectController {

    private final ProjectApiService service;
    private final ClientResolver clients;

    public ProjectController(ProjectApiService service, ClientResolver clients) {
        this.service = service;
        this.clients = clients;
    }

    @PostMapping(value = "/projects", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ProjectDtos.CreatedProjectDto>> upload(@RequestParam("file") MultipartFile file,
                                                                             HttpServletRequest request) {
        ProjectDtos.CreatedProjectDto created = service.create(file, clients.resolve(request));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok("Project uploaded; analysis queued", created));
    }

    @GetMapping("/projects/{projectId}")
    public ApiResponse<ProjectDtos.ProjectDto> project(@PathVariable String projectId, HttpServletRequest request) {
        return ApiResponse.ok("Project", service.project(projectId, clients.resolve(request)));
    }

    @DeleteMapping("/projects/{projectId}")
    public ApiResponse<Void> delete(@PathVariable String projectId, HttpServletRequest request) {
        service.delete(projectId, clients.resolve(request));
        return ApiResponse.ok("Project deleted", null);
    }

    @GetMapping("/jobs/{jobId}")
    public ApiResponse<ProjectDtos.JobDto> job(@PathVariable String jobId, HttpServletRequest request) {
        return ApiResponse.ok("Job", service.job(jobId, clients.resolve(request)));
    }

    @PostMapping("/projects/{projectId}/analyze")
    public ResponseEntity<ApiResponse<ProjectDtos.CreatedProjectDto>> reanalyze(@PathVariable String projectId,
                                                                                HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.ok("Analysis queued", service.reanalyze(projectId, clients.resolve(request))));
    }

    @GetMapping("/projects/{projectId}/analysis")
    public ApiResponse<AnalysisDtos.AnalysisDto> analysis(@PathVariable String projectId, HttpServletRequest request) {
        return ApiResponse.ok("Analysis", service.analysis(projectId, clients.resolve(request)));
    }

    @GetMapping("/projects/{projectId}/dependencies")
    public ApiResponse<GraphDtos.GraphDto> dependencies(@PathVariable String projectId,
                                                        @RequestParam(defaultValue = "5000") int limit,
                                                        HttpServletRequest request) {
        return ApiResponse.ok("Dependency graph", service.dependencies(projectId, clients.resolve(request), limit));
    }

    @GetMapping("/projects/{projectId}/architecture")
    public ApiResponse<ArchitectureDtos.ArchitectureDto> architecture(@PathVariable String projectId, HttpServletRequest request) {
        return ApiResponse.ok("Architecture", service.architecture(projectId, clients.resolve(request)));
    }

    @GetMapping("/projects/{projectId}/modules")
    public ApiResponse<ModuleDtos.ModulesDto> modules(@PathVariable String projectId, HttpServletRequest request) {
        return ApiResponse.ok("Modules", service.modules(projectId, clients.resolve(request)));
    }

    @PutMapping("/projects/{projectId}/modules")
    public ApiResponse<ModuleDtos.ModulesDto> updateModules(@PathVariable String projectId,
                                                            @Valid @RequestBody ModuleDtos.UpdateModulesRequest body,
                                                            HttpServletRequest request) {
        return ApiResponse.ok("Module decisions applied",
                service.updateModules(projectId, body, clients.resolve(request)));
    }

    @PutMapping("/projects/{projectId}/strategy")
    public ApiResponse<PlanDtos.PlanDto> changeStrategy(@PathVariable String projectId,
                                                        @Valid @RequestBody PlanDtos.ChangeStrategyRequest body,
                                                        HttpServletRequest request) {
        return ApiResponse.ok("Target architecture changed; plan rebuilt",
                service.changeStrategy(projectId, body.strategy(), body.addModulithVerification(), clients.resolve(request)));
    }

    @GetMapping("/projects/{projectId}/decisions")
    public ApiResponse<ModuleDtos.DecisionsDto> decisions(@PathVariable String projectId, HttpServletRequest request) {
        return ApiResponse.ok("Review decisions", service.decisions(projectId, clients.resolve(request)));
    }

    @PutMapping("/projects/{projectId}/decisions")
    public ApiResponse<ModuleDtos.DecisionsDto> applyDecisions(@PathVariable String projectId,
                                                               @Valid @RequestBody ModuleDtos.DecisionsDto body,
                                                               HttpServletRequest request) {
        return ApiResponse.ok("Review decisions applied", service.applyDecisions(projectId, body, clients.resolve(request)));
    }

    @GetMapping("/projects/{projectId}/plan")
    public ApiResponse<PlanDtos.PlanDto> plan(@PathVariable String projectId,
                                              @RequestParam(required = false) com.anvith.archmorph.analysis.transformation.target.TargetStrategy strategy,
                                              HttpServletRequest request) {
        if (strategy != null) {
            return ApiResponse.ok("Plan preview (nothing changed)", service.previewPlan(projectId, strategy, clients.resolve(request)));
        }
        return ApiResponse.ok("Transformation plan", service.plan(projectId, clients.resolve(request)));
    }

    @GetMapping("/projects/{projectId}/diff/{entryId}")
    public ApiResponse<PlanDtos.DiffDto> diff(@PathVariable String projectId, @PathVariable String entryId,
                                              HttpServletRequest request) {
        return ApiResponse.ok("Diff", service.diff(projectId, entryId, clients.resolve(request)));
    }

    /**
     * {@code ?dryRun=true} computes the plan, mappings, conflicts and per-file changes in memory and
     * returns them; the transformed workspace is not touched. Otherwise a transformation job is queued.
     */
    @PostMapping("/projects/{projectId}/transform")
    public ResponseEntity<?> transform(@PathVariable String projectId, @RequestParam(defaultValue = "false") boolean dryRun,
                                       HttpServletRequest request) {
        String client = clients.resolve(request);
        if (dryRun) {
            return ResponseEntity.ok(ApiResponse.ok("Dry run completed; nothing was written", service.dryRun(projectId, client)));
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.ok("Transformation queued", service.transform(projectId, client)));
    }

    @PostMapping("/projects/{projectId}/validate")
    public ResponseEntity<ApiResponse<ProjectDtos.CreatedProjectDto>> validate(@PathVariable String projectId,
                                                                               HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.ok("Validation queued", service.validate(projectId, clients.resolve(request))));
    }

    @GetMapping("/projects/{projectId}/validation")
    public ApiResponse<ValidationDtos.ValidationDto> validation(@PathVariable String projectId, HttpServletRequest request) {
        return ApiResponse.ok("Validation report", service.validation(projectId, clients.resolve(request)));
    }

    @GetMapping("/projects/{projectId}/download")
    public ResponseEntity<StreamingResponseBody> download(@PathVariable String projectId, HttpServletRequest request) {
        ProjectApiService.DownloadSource source = service.transformedProject(projectId, clients.resolve(request));
        StreamingResponseBody body = out -> ZipWriter.write(source.directory(), out);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + source.fileName() + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .body(body);
    }

    @GetMapping("/projects/{projectId}/reports/{name}")
    public ResponseEntity<byte[]> report(@PathVariable String projectId, @PathVariable String name,
                                         HttpServletRequest request) throws java.io.IOException {
        ProjectApiService.ReportSource source = service.report(projectId, name, clients.resolve(request));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(source.name().contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + source.downloadName() + "\"")
                .body(Files.readAllBytes(source.file()));
    }
}
