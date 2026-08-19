package com.anvith.archmorph.analysis.transformation.packaging;

import com.anvith.archmorph.analysis.transformation.FolderType;
import org.springframework.stereotype.Service;

@Service
public class DefaultPackagePlanner
        implements PackagePlanner {

    /**
     * Build the destination package.
     *
     * Example:
     *
     * Base Package:
     *      com.demo
     *
     * Module:
     *      user
     *
     * Folder:
     *      service
     *
     * Result:
     *
     * com.demo.user.service
     */
    @Override
    public String plan(

            String basePackage,

            String moduleName,

            FolderType folderType) {

        if (basePackage == null) {
            basePackage = "";
        }

        if (moduleName == null ||
                moduleName.isBlank()) {

            moduleName = "common";

        }

        if (folderType == null) {

            return basePackage
                    + "."
                    + moduleName;

        }

        StringBuilder builder =
                new StringBuilder();

        builder.append(basePackage);

        if (!basePackage.isBlank()) {
            builder.append(".");
        }

        builder.append(moduleName.toLowerCase());

        builder.append(".");

        builder.append(
                folderType.getFolderName()
        );

        return builder.toString();

    }

}