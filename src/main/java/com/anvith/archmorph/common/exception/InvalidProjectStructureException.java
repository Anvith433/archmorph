package com.anvith.archmorph.common.exception;

public class InvalidProjectStructureException extends ArchMorphException {

    public InvalidProjectStructureException(String message) {
        super(ErrorCode.INVALID_PROJECT_STRUCTURE, message, "Upload a Maven project: the archive must contain a pom.xml and src/main/java.");
    }

    public InvalidProjectStructureException(String message, Throwable cause) {
        super(ErrorCode.INVALID_PROJECT_STRUCTURE, message, "Upload a Maven project: the archive must contain a pom.xml and src/main/java.", cause);
    }
}
