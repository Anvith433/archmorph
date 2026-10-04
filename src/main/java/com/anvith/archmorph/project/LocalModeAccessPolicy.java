package com.anvith.archmorph.project;

import org.springframework.stereotype.Component;

/** Default policy for the local, single-user mode: possession of the random project ID is the capability. */
@Component
public class LocalModeAccessPolicy implements ProjectAccessPolicy {

    @Override
    public void check(ProjectSession session, String clientId) {
        // intentionally permissive; see ProjectAccessPolicy
    }
}
