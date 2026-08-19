package com.anvith.archmorph.analysis.dependency.extractor;

import com.anvith.archmorph.analysis.dependency.DependencyGraph;
import com.anvith.archmorph.analysis.dependency.DependencyNode;
import com.github.javaparser.ast.CompilationUnit;

/**
 * Common contract for all dependency extractors.
 *
 * Every extractor is responsible for finding
 * one specific type of dependency.
 *
 * Examples:
 *  - Field Dependencies
 *  - Constructor Dependencies
 *  - Method Dependencies
 *  - Inheritance
 *  - Interface Implementation
 */
public interface DependencyExtractor {

    /**
     * Extract dependencies from the given
     * CompilationUnit and add them into
     * the dependency graph.
     *
     * @param compilationUnit Parsed Java source
     * @param sourceNode      Current class
     * @param graph           Dependency graph
     */
    void extract(
            CompilationUnit compilationUnit,
            DependencyNode sourceNode,
            DependencyGraph graph
    );

}