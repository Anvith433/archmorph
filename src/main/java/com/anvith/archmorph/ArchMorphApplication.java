package com.anvith.archmorph;

import com.anvith.archmorph.cli.CliRunner;
import com.anvith.archmorph.common.config.ArchMorphProperties;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * ArchMorph entry point. Started without arguments (or with Spring options) it runs the web server;
 * started with {@code analyze|plan|transform} it runs the command-line interface and exits, without
 * starting a web server.
 */
@SpringBootApplication
@EnableConfigurationProperties(ArchMorphProperties.class)
public class ArchMorphApplication {

    public static void main(String[] args) throws IOException {
        if (CliRunner.isCliInvocation(args)) {
            Path workspace = Files.createTempDirectory("archmorph-cli-");
            SpringApplication application = new SpringApplication(ArchMorphApplication.class);
            application.setWebApplicationType(WebApplicationType.NONE);
            application.setBannerMode(Banner.Mode.OFF);
            application.setDefaultProperties(Map.of(
                    "archmorph.cli.enabled", "true",
                    "archmorph.workspace.root", workspace.toString(),
                    "spring.main.log-startup-info", "false",
                    "logging.level.root", "WARN",
                    "archmorph.validation.build.mode", "COMPILE"));
            int exit = SpringApplication.exit(application.run(args));
            System.exit(exit);
        }
        SpringApplication.run(ArchMorphApplication.class, args);
    }

}
