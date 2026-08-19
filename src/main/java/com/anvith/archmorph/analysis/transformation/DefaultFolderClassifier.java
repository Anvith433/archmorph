package com.anvith.archmorph.analysis.transformation;

import com.anvith.archmorph.parser.ComponentType;
import org.springframework.stereotype.Service;

@Service
public class DefaultFolderClassifier
        implements FolderClassifier {

    @Override
    public FolderType classify(
            ComponentType componentType) {

        if (componentType == null) {
            return FolderType.COMMON;
        }

        switch (componentType) {

            case CONTROLLER:
                return FolderType.CONTROLLER;

            case SERVICE:
                return FolderType.SERVICE;

            case REPOSITORY:
                return FolderType.REPOSITORY;

            case ENTITY:
                return FolderType.ENTITY;

            case DTO:
                return FolderType.DTO;

            case CONFIGURATION:
                return FolderType.CONFIGURATION;

            case COMPONENT:
                return FolderType.COMPONENT;

            case EXCEPTION:
            case EXCEPTION_HANDLER:
                return FolderType.EXCEPTION;

            default:
                return FolderType.COMMON;
        }
    }

}