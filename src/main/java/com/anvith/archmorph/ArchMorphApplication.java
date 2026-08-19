package com.anvith.archmorph;

import com.anvith.archmorph.common.config.WorkspaceProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(WorkspaceProperties.class)
public class ArchMorphApplication {

    public static void main(String[] args) {
        SpringApplication.run(ArchMorphApplication.class, args);
    }

}