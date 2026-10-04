package com.anvith.archmorph.job;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryJobStore implements JobStore {

    private final Map<String, Job> jobs = new ConcurrentHashMap<>();

    @Override
    public void put(Job job) {
        jobs.put(job.id(), job);
    }

    @Override
    public Optional<Job> find(String jobId) {
        return Optional.ofNullable(jobId == null ? null : jobs.get(jobId));
    }

    @Override
    public Collection<Job> all() {
        return List.copyOf(jobs.values());
    }

    @Override
    public void removeForProject(String projectId) {
        jobs.values().removeIf(j -> j.projectId().equals(projectId));
    }

    @Override
    public long countActiveBy(String ownerId) {
        return jobs.values().stream().filter(j -> j.ownerId().equals(ownerId) && !j.status().isTerminal()).count();
    }
}
