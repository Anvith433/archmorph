package com.anvith.archmorph.analysis.transformation;

import com.anvith.archmorph.parser.ComponentType;

public interface FolderClassifier {

    FolderType classify(
            ComponentType componentType
    );

}