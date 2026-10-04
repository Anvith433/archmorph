package com.anvith.archmorph.security;

import com.anvith.archmorph.common.config.ArchMorphProperties;
import com.anvith.archmorph.job.JobStore;
import com.anvith.archmorph.project.ProjectSession;
import com.anvith.archmorph.project.ProjectSessionStore;
import com.anvith.archmorph.workspace.WorkspaceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Deletes projects (uploaded source, generated project, reports) after the configured retention
 * period, including workspaces left behind by a previous server run.
 */
@Component
@EnableScheduling
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class RetentionService {

    private static final Logger log = LoggerFactory.getLogger(RetentionService.class);

    private final ArchMorphProperties properties;
    private final WorkspaceManager workspaceManager;
    private final ProjectSessionStore sessions;
    private final JobStore jobs;

    public RetentionService(ArchMorphProperties properties, WorkspaceManager workspaceManager,
                            ProjectSessionStore sessions, JobStore jobs) {
        this.properties = properties;
        this.workspaceManager = workspaceManager;
        this.sessions = sessions;
        this.jobs = jobs;
    }

    @Scheduled(fixedDelayString = "${archmorph.workspace.cleanup-interval:PT15M}",
            initialDelayString = "${archmorph.workspace.cleanup-interval:PT15M}")
    public void cleanUp() {
        Instant cutoff = Instant.now().minus(properties.getWorkspace().getRetention());
        int removed = 0;
        for (ProjectSession session : sessions.all()) {
            if (session.lastActivity().isBefore(cutoff) && session.createdAt().isBefore(cutoff)) {
                sessions.remove(session.projectId());
                jobs.removeForProject(session.projectId());
                workspaceManager.delete(session.projectId());
                removed++;
            }
        }
        for (String id : workspaceManager.expiredProjects(cutoff)) {
            if (sessions.find(id).isEmpty()) {
                workspaceManager.delete(id);
                removed++;
            }
        }
        if (removed > 0) {
            log.info("Retention cleanup removed {} expired project(s)", removed);
        }
    }
}
