package com.anvith.archmorph.analysis.transformation.packaging;

import java.util.Collection;

/**
 * Resolves the common root package
 * shared by all Java source packages
 * in a project.
 *
 * Examples:
 *
 * com.pmj.template.controller
 * com.pmj.template.service
 * com.pmj.template.repository
 *
 * Result:
 * com.pmj.template
 *
 * This interface is completely generic
 * and independent of Spring Boot,
 * business modules, or project type.
 */
public interface BasePackageResolver {

    /**
     * Determine the common root package
     * shared by all source packages.
     *
     * @param packages Collection of Java packages
     * @return Common root package
     */
    String resolve(
            Collection<String> packages
    );

}