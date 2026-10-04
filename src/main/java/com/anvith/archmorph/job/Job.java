package com.anvith.archmorph.job;

import com.anvith.archmorph.pipeline.ProgressEvent;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** A unit of background work with observable status and structured progress events. */
public class Job {

    private final String id;
    private final String projectId;
    private final JobType type;
    private final String ownerId;
    private final Instant createdAt = Instant.now();
    private final List<JobEvent> events = new CopyOnWriteArrayList<>();

    private volatile JobStatus status = JobStatus.QUEUED;
    private volatile Instant startedAt;
    private volatile Instant finishedAt;
    private volatile String errorCode;
    private volatile String errorMessage;
    private volatile String errorHint;

    public Job(String id, String projectId, JobType type, String ownerId) {
        this.id = id;
        this.projectId = projectId;
        this.type = type;
        this.ownerId = ownerId;
    }

    public void transition(JobStatus next) {
        if (status.isTerminal()) {
            return;
        }
        if (startedAt == null && next != JobStatus.QUEUED) {
            startedAt = Instant.now();
        }
        status = next;
        if (next.isTerminal()) {
            finishedAt = Instant.now();
        }
    }

    public void fail(String code, String message, String hint) {
        errorCode = code;
        errorMessage = message;
        errorHint = hint;
        transition(JobStatus.FAILED);
    }

    public void addEvent(ProgressEvent event, String detail) {
        events.add(new JobEvent(Instant.now(), event, detail));
    }

    public String id() {
        return id;
    }

    public String projectId() {
        return projectId;
    }

    public JobType type() {
        return type;
    }

    public String ownerId() {
        return ownerId;
    }

    public JobStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant finishedAt() {
        return finishedAt;
    }

    public String errorCode() {
        return errorCode;
    }

    public String errorMessage() {
        return errorMessage;
    }

    public String errorHint() {
        return errorHint;
    }

    public List<JobEvent> events() {
        return List.copyOf(events);
    }
}
