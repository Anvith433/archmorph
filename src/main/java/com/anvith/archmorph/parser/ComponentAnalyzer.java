package com.anvith.archmorph.parser;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ComponentAnalyzer {

    public ClassMetadata analyze(CompilationUnit compilationUnit) {

        ClassMetadata metadata = new ClassMetadata();

        metadata.setPackageName(
                compilationUnit.getPackageDeclaration()
                        .map(pd -> pd.getNameAsString())
                        .orElse("")
        );

        Optional<ClassOrInterfaceDeclaration> optionalClass =
                compilationUnit.findFirst(ClassOrInterfaceDeclaration.class);

        if (optionalClass.isEmpty()) {
            metadata.setComponentType(ComponentType.UNKNOWN);
            return metadata;
        }

        ClassOrInterfaceDeclaration clazz = optionalClass.get();

        metadata.setClassName(clazz.getNameAsString());

        // NEW
        metadata.setInterface(clazz.isInterface());

        clazz.getAnnotations().forEach(annotation ->
                metadata.getAnnotations().add(annotation.getNameAsString())
        );

        clazz.getExtendedTypes().forEach(type ->
                metadata.setSuperClass(type.getNameAsString())
        );

        clazz.getImplementedTypes().forEach(type ->
                metadata.getInterfaces().add(type.getNameAsString())
        );

        compilationUnit.getImports().forEach(importDeclaration ->
                metadata.getImports().add(importDeclaration.getNameAsString())
        );

        clazz.getFields().forEach(field ->
                metadata.getFields().add(field.toString())
        );

        clazz.getMethods().forEach(method ->
                metadata.getMethods().add(method.getNameAsString())
        );

        metadata.setComponentType(detectComponent(metadata));

        return metadata;
    }

    /**
     * Detection Priority
     *
     * Annotation
     * ↓
     * Inheritance
     * ↓
     * Interface
     * ↓
     * Naming
     * ↓
     * Package
     */
    private ComponentType detectComponent(ClassMetadata metadata) {

        ComponentType type;

        type = detectByAnnotation(metadata);
        if (type != ComponentType.UNKNOWN) {
            return type;
        }

        type = detectByInheritance(metadata);
        if (type != ComponentType.UNKNOWN) {
            return type;
        }

        type = detectByInterface(metadata);
        if (type != ComponentType.UNKNOWN) {
            return type;
        }

        type = detectByNaming(metadata);
        if (type != ComponentType.UNKNOWN) {
            return type;
        }

        return detectByPackage(metadata);
    }

    /**
     * Annotation Detection
     */
    private ComponentType detectByAnnotation(ClassMetadata metadata) {

        List<String> annotations = metadata.getAnnotations();

        if (annotations.contains(AnnotationConstants.REST_CONTROLLER))
            return ComponentType.CONTROLLER;

        if (annotations.contains(AnnotationConstants.CONTROLLER))
            return ComponentType.CONTROLLER;

        if (annotations.contains(AnnotationConstants.SERVICE))
            return ComponentType.SERVICE;

        if (annotations.contains(AnnotationConstants.REPOSITORY))
            return ComponentType.REPOSITORY;

        if (annotations.contains(AnnotationConstants.ENTITY))
            return ComponentType.ENTITY;

        if (annotations.contains(AnnotationConstants.CONFIGURATION))
            return ComponentType.CONFIGURATION;

        if (annotations.contains(AnnotationConstants.COMPONENT))
            return ComponentType.COMPONENT;

        if (annotations.contains(AnnotationConstants.REST_CONTROLLER_ADVICE))
            return ComponentType.EXCEPTION_HANDLER;

        if (annotations.contains(AnnotationConstants.CONTROLLER_ADVICE))
            return ComponentType.EXCEPTION_HANDLER;

        if (annotations.contains(AnnotationConstants.SPRING_BOOT_APPLICATION))
            return ComponentType.APPLICATION;

        return ComponentType.UNKNOWN;
    }

    /**
     * Inheritance Detection
     */
    private ComponentType detectByInheritance(ClassMetadata metadata) {

        String superClass = metadata.getSuperClass();

        if (superClass == null)
            return ComponentType.UNKNOWN;

        if ("RuntimeException".equals(superClass))
            return ComponentType.EXCEPTION;

        if ("Exception".equals(superClass))
            return ComponentType.EXCEPTION;

        if ("OncePerRequestFilter".equals(superClass))
            return ComponentType.FILTER;

        return ComponentType.UNKNOWN;
    }

    /**
     * Interface Detection
     */
    private ComponentType detectByInterface(ClassMetadata metadata) {

        List<String> interfaces = metadata.getInterfaces();

        if (interfaces.contains("JpaRepository"))
            return ComponentType.REPOSITORY;

        if (interfaces.contains("UserDetailsService"))
            return ComponentType.SERVICE;

        return ComponentType.UNKNOWN;
    }

    /**
     * Naming Detection
     */
    private ComponentType detectByNaming(ClassMetadata metadata) {

        String className = metadata.getClassName();

        if (className == null)
            return ComponentType.UNKNOWN;

        /*
         * Don't classify interfaces as Services
         */
        if (metadata.isInterface()) {

            if (className.endsWith("Repository"))
                return ComponentType.REPOSITORY;

            return ComponentType.UNKNOWN;
        }

        if (className.endsWith("Controller"))
            return ComponentType.CONTROLLER;

        if (className.endsWith("ServiceImpl"))
            return ComponentType.SERVICE;

        if (className.endsWith("Repository"))
            return ComponentType.REPOSITORY;

        if (className.endsWith("Entity"))
            return ComponentType.ENTITY;

        if (className.endsWith("Dto"))
            return ComponentType.DTO;

        if (className.endsWith("Request"))
            return ComponentType.DTO;

        if (className.endsWith("Response"))
            return ComponentType.DTO;

        if (className.endsWith("Exception"))
            return ComponentType.EXCEPTION;

        if (className.endsWith("Config"))
            return ComponentType.CONFIGURATION;

        if (className.endsWith("Configuration"))
            return ComponentType.CONFIGURATION;

        return ComponentType.UNKNOWN;
    }

    /**
     * Package Detection
     */
    private ComponentType detectByPackage(ClassMetadata metadata) {

        String packageName = metadata.getPackageName();

        if (packageName == null || packageName.isBlank())
            return ComponentType.UNKNOWN;

        String[] packages = packageName.split("\\.");

        String lastPackage = packages[packages.length - 1].toLowerCase();

        switch (lastPackage) {

            case "controller":
                return ComponentType.CONTROLLER;

            case "service":
                return ComponentType.SERVICE;

            case "repository":
                return ComponentType.REPOSITORY;

            case "entity":
                return ComponentType.ENTITY;

            case "dto":
                return ComponentType.DTO;

            case "exception":
                return ComponentType.EXCEPTION;

            case "config":
                return ComponentType.CONFIGURATION;

            default:
                return ComponentType.UNKNOWN;
        }
    }
}