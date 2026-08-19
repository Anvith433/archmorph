package com.anvith.archmorph.common.config;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "workspace")
public class WorkspaceProperties {

    /**
     * Root workspace directory
     * Example: workspace/
     */
    private Path root;

    /**
     * Stores uploaded ZIP archives
     */
    private String archives;

    /**
     * Stores individual project workspaces
     */
    private String projects;

    /**
     * Temporary files
     */
    private String temp;

}