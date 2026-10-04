package com.anvith.archmorph.project;

import java.util.Collection;
import java.util.Optional;

/** Storage of project sessions. In-memory today; a persistent implementation can replace it later. */
public interface ProjectSessionStore {

    void put(ProjectSession session);

    Optional<ProjectSession> find(String projectId);

    void remove(String projectId);

    Collection<ProjectSession> all();

    /** Number of sessions owned by the client that are not finished. */
    long countActiveBy(String ownerId);
}
