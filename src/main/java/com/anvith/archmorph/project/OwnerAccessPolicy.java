package com.anvith.archmorph.project;

import com.anvith.archmorph.common.exception.ErrorCode;
import com.anvith.archmorph.common.exception.NotFoundException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Policy when authentication is enabled: a project belongs to the user who uploaded it. Another user's project
 * is reported as not found, so project IDs cannot be probed.
 */
@Component
@ConditionalOnProperty(name = "archmorph.security.auth.mode", havingValue = "basic")
public class OwnerAccessPolicy implements ProjectAccessPolicy {

    @Override
    public void check(ProjectSession session, String clientId) {
        if (clientId == null || !clientId.equals(session.ownerId())) {
            throw new NotFoundException(ErrorCode.PROJECT_NOT_FOUND, "Project not found.");
        }
    }
}
