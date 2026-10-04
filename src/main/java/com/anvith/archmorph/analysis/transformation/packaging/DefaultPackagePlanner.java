package com.anvith.archmorph.analysis.transformation.packaging;

import com.anvith.archmorph.analysis.transformation.FolderType;
import org.springframework.stereotype.Service;

/**
 * Plain package arithmetic for a business module:
 * {@code <base>.<modulesPackage>.<module>.<folder>}. The full layout (shared code,
 * application class) lives in {@code TargetArchitecture}.
 */
@Service
public class DefaultPackagePlanner implements PackagePlanner {

    private static final String MODULES = "modules";

    @Override
    public String plan(String basePackage, String moduleName, FolderType folderType) {
        String base = basePackage == null ? "" : basePackage;
        String module = moduleName == null || moduleName.isBlank() ? "common" : moduleName.toLowerCase();
        StringBuilder builder = new StringBuilder(base);
        append(builder, MODULES);
        append(builder, module);
        if (folderType != null) {
            append(builder, folderType.getFolderName());
        }
        return builder.toString();
    }

    private static void append(StringBuilder builder, String segment) {
        if (!builder.isEmpty()) {
            builder.append('.');
        }
        builder.append(segment);
    }
}
