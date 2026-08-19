package com.anvith.archmorph.analysis.registry;

public interface ProjectClassRegistry {

    /**
     * Register one discovered class.
     */
    void register(ProjectClassInfo classInfo);

    /**
     * Find class metadata by class name.
     */
    ProjectClassInfo find(String className);

    /**
     * Check whether a class exists.
     */
    boolean contains(String className);

    /**
     * Remove all registered classes.
     */
    void clear();

}