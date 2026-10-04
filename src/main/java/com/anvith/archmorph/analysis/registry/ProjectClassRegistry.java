package com.anvith.archmorph.analysis.registry;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Index of every type declared in one analysed project.
 *
 * <p>A registry belongs to exactly one analysis. It is not a Spring
 * singleton: sharing one between concurrent analyses corrupted results in
 * earlier versions.</p>
 */
public interface ProjectClassRegistry {

    /** Register one discovered class. */
    void register(ProjectClassInfo classInfo);

    /**
     * Find class metadata by fully-qualified name, or by simple name when
     * exactly one class has that simple name.
     */
    ProjectClassInfo find(String className);

    /** Check whether a class exists (qualified or unique simple name). */
    boolean contains(String className);

    /** Remove all registered classes. */
    void clear();

    Optional<ProjectClassInfo> findByQualifiedName(String qualifiedName);

    List<ProjectClassInfo> findBySimpleName(String simpleName);

    Collection<ProjectClassInfo> all();

    /** Every package that declares at least one project class. */
    Set<String> packages();

    /** Classes declared directly in the given package (top-level only). */
    List<ProjectClassInfo> classesInPackage(String packageName);
}
