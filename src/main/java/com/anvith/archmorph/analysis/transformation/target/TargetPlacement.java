package com.anvith.archmorph.analysis.transformation.target;

import com.anvith.archmorph.analysis.transformation.FolderType;

/** Where a class goes in the target architecture. */
public record TargetPlacement(String targetPackage, FolderType folder) {
}
