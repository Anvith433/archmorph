package com.anvith.archmorph.parser;

import java.nio.file.Path;
import java.util.List;

/**
 * Detected layout of an uploaded project.
 *
 * <p>Paths are absolute and server-side only; API mappers expose them
 * relative to the project root.</p>
 */
public record ProjectStructure(
        Path projectRoot,
        BuildTool buildTool,
        String groupId,
        String artifactId,
        boolean springBoot,
        boolean multiModule,
        List<String> mavenModules,
        List<Path> mainSourceRoots,
        List<Path> testSourceRoots,
        List<Path> resourceRoots,
        boolean containsKotlin,
        boolean containsGroovyOrScala,
        boolean usesAnnotationProcessors,
        List<String> warnings) {

    public enum BuildTool {
        MAVEN,
        GRADLE,
        NONE
    }
}
