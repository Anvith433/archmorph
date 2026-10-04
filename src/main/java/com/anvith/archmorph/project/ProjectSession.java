package com.anvith.archmorph.project;

import com.anvith.archmorph.analysis.module.ModuleDiscoveryReport;
import com.anvith.archmorph.analysis.module.editing.ModuleEdit;
import com.anvith.archmorph.analysis.transformation.TransformationResult;
import com.anvith.archmorph.analysis.transformation.planner.TransformationPlan;
import com.anvith.archmorph.analysis.validation.ValidationReport;
import com.anvith.archmorph.pipeline.AnalysisResult;
import com.anvith.archmorph.workspace.ProjectWorkspace;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Mutable, in-memory state of one project: the analysis, the user's module decisions and the
 * results of each later phase. All access goes through {@link #lock()}; callers must hold it when
 * reading several fields that belong together.
 *
 * <p>Persisted artefacts (reports) live in the project workspace; this object only caches parsed
 * data that is expensive to rebuild. It can always be rebuilt from {@code original/} by re-analysis.</p>
 */
public class ProjectSession {

    private final ProjectWorkspace workspace;
    private final String displayName;
    private final String ownerId;
    private final Instant createdAt;
    private final long archiveBytes;
    private final ReentrantLock lock = new ReentrantLock();

    private volatile ProjectStatus status = ProjectStatus.QUEUED;
    private volatile String failureCode;
    private volatile String failureMessage;
    private volatile String failureHint;
    private volatile String latestJobId;

    private AnalysisResult analysis;
    private List<ModuleEdit> decisions = new ArrayList<>();
    private ModuleDiscoveryReport finalModules;
    private TransformationPlan plan;
    private TransformationResult transformation;
    private ValidationReport validation;
    private boolean transformedAvailable;
    private Instant lastActivity = Instant.now();

    public ProjectSession(ProjectWorkspace workspace, String displayName, String ownerId, long archiveBytes) {
        this.workspace = workspace;
        this.displayName = displayName;
        this.ownerId = ownerId;
        this.archiveBytes = archiveBytes;
        this.createdAt = Instant.now();
    }

    public ReentrantLock lock() {
        return lock;
    }

    public void touch() {
        lastActivity = Instant.now();
    }

    public String projectId() {
        return workspace.projectId();
    }

    public ProjectWorkspace workspace() {
        return workspace;
    }

    public String displayName() {
        return displayName;
    }

    public String ownerId() {
        return ownerId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public long archiveBytes() {
        return archiveBytes;
    }

    public Instant lastActivity() {
        return lastActivity;
    }

    public ProjectStatus status() {
        return status;
    }

    public void setStatus(ProjectStatus status) {
        this.status = status;
        touch();
    }

    public void fail(String code, String message, String hint) {
        this.failureCode = code;
        this.failureMessage = message;
        this.failureHint = hint;
        setStatus(ProjectStatus.FAILED);
    }

    public void clearFailure() {
        failureCode = null;
        failureMessage = null;
        failureHint = null;
    }

    public String failureCode() {
        return failureCode;
    }

    public String failureMessage() {
        return failureMessage;
    }

    public String failureHint() {
        return failureHint;
    }

    public String latestJobId() {
        return latestJobId;
    }

    public void setLatestJobId(String latestJobId) {
        this.latestJobId = latestJobId;
    }

    public AnalysisResult analysis() {
        return analysis;
    }

    public void setAnalysis(AnalysisResult analysis) {
        this.analysis = analysis;
    }

    public List<ModuleEdit> decisions() {
        return decisions;
    }

    public void setDecisions(List<ModuleEdit> decisions) {
        this.decisions = new ArrayList<>(decisions);
    }

    public ModuleDiscoveryReport finalModules() {
        return finalModules;
    }

    public void setFinalModules(ModuleDiscoveryReport finalModules) {
        this.finalModules = finalModules;
    }

    public TransformationPlan plan() {
        return plan;
    }

    public void setPlan(TransformationPlan plan) {
        this.plan = plan;
    }

    public TransformationResult transformation() {
        return transformation;
    }

    public void setTransformation(TransformationResult transformation) {
        this.transformation = transformation;
    }

    public ValidationReport validation() {
        return validation;
    }

    public void setValidation(ValidationReport validation) {
        this.validation = validation;
    }

    public boolean transformedAvailable() {
        return transformedAvailable;
    }

    public void setTransformedAvailable(boolean transformedAvailable) {
        this.transformedAvailable = transformedAvailable;
    }

    public boolean analysed() {
        return analysis != null;
    }

    /** Drop everything derived from the module decisions (plan, transformed project, validation). */
    public void invalidateTransformation() {
        plan = null;
        transformation = null;
        validation = null;
        transformedAvailable = false;
    }
}
