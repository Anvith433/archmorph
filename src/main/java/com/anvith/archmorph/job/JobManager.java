package com.anvith.archmorph.job;

import com.anvith.archmorph.common.config.ArchMorphProperties;
import com.anvith.archmorph.common.exception.ArchMorphException;
import com.anvith.archmorph.common.exception.ErrorCode;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Runs jobs on a bounded worker pool. Submissions are rejected (not silently queued forever) when the
 * queue is full or when the client already owns too many active jobs.
 */
@Service
public class JobManager {

    private static final Logger log = LoggerFactory.getLogger(JobManager.class);

    private final JobStore store;
    private final ArchMorphProperties properties;
    private final ThreadPoolExecutor executor;

    public JobManager(JobStore store, ArchMorphProperties properties) {
        this.store = store;
        this.properties = properties;
        ArchMorphProperties.Jobs jobs = properties.getJobs();
        this.executor = new ThreadPoolExecutor(jobs.getWorkerThreads(), jobs.getWorkerThreads(), 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(jobs.getMaxQueuedJobs()), runnable -> {
            Thread thread = new Thread(runnable, "archmorph-job");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Fail fast, before any expensive work, when the client may not start another job. */
    public void assertCapacity(String ownerId) {
        if (store.countActiveBy(ownerId) >= properties.getJobs().getMaxActiveJobsPerClient()) {
            throw new ArchMorphException(ErrorCode.TOO_MANY_JOBS,
                    "Too many analysis jobs are already running for your connection.",
                    "Wait for a running job to finish and try again.");
        }
    }

    /** Create and enqueue a job. {@code work} receives the job to update its status and events. */
    public Job submit(String projectId, JobType type, String ownerId, Consumer<Job> work) {
        if (store.countActiveBy(ownerId) >= properties.getJobs().getMaxActiveJobsPerClient()) {
            throw new ArchMorphException(ErrorCode.TOO_MANY_JOBS,
                    "Too many analysis jobs are already running for your connection.",
                    "Wait for a running job to finish and try again.");
        }
        Job job = new Job(UUID.randomUUID().toString(), projectId, type, ownerId);
        store.put(job);
        try {
            executor.execute(() -> run(job, work));
        } catch (RejectedExecutionException e) {
            job.fail(ErrorCode.TOO_MANY_JOBS.name(), "The server is busy.", "Try again in a few minutes.");
            throw new ArchMorphException(ErrorCode.TOO_MANY_JOBS, "The server is busy with other analyses.",
                    "Try again in a few minutes.");
        }
        return job;
    }

    private void run(Job job, Consumer<Job> work) {
        try {
            work.accept(job);
            if (!job.status().isTerminal()) {
                job.transition(JobStatus.COMPLETED);
            }
        } catch (ArchMorphException e) {
            log.info("Job {} failed with {}", job.id(), e.getErrorCode());
            job.fail(e.getErrorCode().name(), e.getMessage(), e.getHint());
        } catch (Throwable t) {
            log.error("Job {} failed unexpectedly", job.id(), t);
            job.fail(ErrorCode.INTERNAL_ERROR.name(), "The job failed unexpectedly.", null);
        }
    }

    public Job require(String jobId) {
        return store.find(jobId).orElseThrow(() ->
                new com.anvith.archmorph.common.exception.NotFoundException(ErrorCode.JOB_NOT_FOUND, "Job not found."));
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
