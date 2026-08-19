package com.anvith.archmorph.analysis.transformation.packaging;

import com.anvith.archmorph.analysis.transformation.FolderType;

/**
 * Plans the destination package
 * of every Java class.
 *
 * Example
 *
 * Base Package
 *      com.demo
 *
 * Module
 *      user
 *
 * Folder
 *      service
 *
 * Result
 *
 * com.demo.user.service
 */
public interface PackagePlanner {

    /**
     * Compute the target package
     * for one Java class.
     *
     * @param basePackage Common project package
     * @param moduleName Business module
     * @param folderType Folder inside module
     * @return Target package
     */
    String plan(

            String basePackage,

            String moduleName,

            FolderType folderType

    );

}