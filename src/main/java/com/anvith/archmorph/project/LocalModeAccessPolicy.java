package com.anvith.archmorph.project;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Default policy for the local, single-user mode: possession of the random project ID is the capability. */
@Component
@ConditionalOnProperty(name = "archmorph.security.auth.mode", havingValue = "none", matchIfMissing = true)
public class LocalModeAccessPolicy implements ProjectAccessPolicy {

    @Override
    public void check(ProjectSession session, String clientId) {
        // intentionally permissive; see ProjectAccessPolicy
    }
}
