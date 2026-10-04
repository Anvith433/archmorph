package com.anvith.archmorph.analysis.transformation;

import com.anvith.archmorph.parser.ComponentType;
import org.springframework.stereotype.Service;

@Service
public class DefaultFolderClassifier implements FolderClassifier {

    @Override
    public FolderType classify(ComponentType componentType) {
        if (componentType == null) {
            return FolderType.COMMON;
        }
        return switch (componentType) {
            case CONTROLLER -> FolderType.CONTROLLER;
            case SERVICE -> FolderType.SERVICE;
            case REPOSITORY -> FolderType.REPOSITORY;
            case ENTITY -> FolderType.ENTITY;
            case DTO -> FolderType.DTO;
            case CONFIGURATION -> FolderType.CONFIGURATION;
            case SECURITY, FILTER -> FolderType.SECURITY;
            case COMPONENT -> FolderType.COMPONENT;
            case EXCEPTION, EXCEPTION_HANDLER -> FolderType.EXCEPTION;
            default -> FolderType.COMMON;
        };
    }
}
