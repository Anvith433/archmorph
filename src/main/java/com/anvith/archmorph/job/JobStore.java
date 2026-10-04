package com.anvith.archmorph.job;

import java.util.Collection;
import java.util.Optional;

/** Storage of jobs. In-memory today; designed to be replaced by a persistent store. */
public interface JobStore {

    void put(Job job);

    Optional<Job> find(String jobId);

    Collection<Job> all();

    void removeForProject(String projectId);

    /** Jobs of the owner that are queued or running. */
    long countActiveBy(String ownerId);
}
