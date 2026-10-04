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

    /**
     * Target package of a class, knowing whether code outside its module uses it.
     *
     * @param exposed true when a class of another module (or shared / application code) depends on it
     */
    default TargetPlacement place(String basePackage, ClassAssignment assignment, ComponentType type, String currentPackage,
                                  boolean exposed) {
        return place(basePackage, assignment, type, currentPackage);
    }

    /** Root package of a business module (everything of the module lives in or below it). */
    String moduleRoot(String basePackage, String module);

    /** Root package of shared code. */
    String sharedRoot(String basePackage);

    /** True when the layout separates a module's public API (the module root package) from its internals. */
    default boolean separatesApi() {
        return false;
    }

    /** Illustrative tree of the layout for documentation and the UI. */
    List<String> describeLayout(String basePackage, List<String> businessModules);
}
