package com.anvith.archmorph.project;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryProjectSessionStore implements ProjectSessionStore {

    private final Map<String, ProjectSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void put(ProjectSession session) {
        sessions.put(session.projectId(), session);
    }

    @Override
    public Optional<ProjectSession> find(String projectId) {
        return Optional.ofNullable(projectId == null ? null : sessions.get(projectId));
    }

    @Override
    public void remove(String projectId) {
        sessions.remove(projectId);
    }

    @Override
    public Collection<ProjectSession> all() {
        return List.copyOf(sessions.values());
    }

    @Override
    public long countActiveBy(String ownerId) {
        return sessions.values().stream()
                .filter(s -> s.ownerId().equals(ownerId))
                .filter(s -> s.status() == ProjectStatus.QUEUED || s.status() == ProjectStatus.ANALYZING
                        || s.status() == ProjectStatus.PLANNING || s.status() == ProjectStatus.TRANSFORMING
                        || s.status() == ProjectStatus.VALIDATING)
                .count();
    }
}
