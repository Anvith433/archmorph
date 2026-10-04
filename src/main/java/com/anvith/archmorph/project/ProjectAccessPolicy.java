package com.anvith.archmorph.project;

/**
 * Decides whether a caller may access a project. The local no-login mode allows everything because
 * project IDs are unguessable random UUIDs. When authentication is added, provide an implementation
 * that compares the authenticated principal with {@link ProjectSession#ownerId()}; no business
 * logic needs to change.
 */
public interface ProjectAccessPolicy {

    /** @throws com.anvith.archmorph.common.exception.NotFoundException (never "forbidden") to avoid revealing IDs */
    void check(ProjectSession session, String clientId);
}
