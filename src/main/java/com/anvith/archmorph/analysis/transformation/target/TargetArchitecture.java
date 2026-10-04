package com.anvith.archmorph.analysis.transformation.target;

import com.anvith.archmorph.analysis.module.ClassAssignment;
import com.anvith.archmorph.parser.ComponentType;

import java.util.List;

/**
 * Describes the target package layout. The planner depends on this
 * abstraction only; no layout is hard-coded anywhere else.
 */
public interface TargetArchitecture {

    TargetStrategy strategy();

    /**
     * Target package of a class.
     *
     * @param basePackage   common project root package (may be empty)
     * @param assignment    module decision for the class
     * @param type          architectural role of the class
     * @param currentPackage current package, used for classes that must stay where they are
     */
    TargetPlacement place(String basePackage, ClassAssignment assignment, ComponentType type, String currentPackage);

    /** Illustrative tree of the layout for documentation and the UI. */
    List<String> describeLayout(String basePackage, List<String> businessModules);
}
