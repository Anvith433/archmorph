package com.anvith.archmorph.common.exception;

public class SourceCodeNotFoundException extends ArchMorphException {

    public SourceCodeNotFoundException(String message) {
        super(ErrorCode.SOURCE_NOT_FOUND, message, "Make sure the archive contains src/main/java with at least one .java file.");
    }

    public SourceCodeNotFoundException(String message, Throwable cause) {
        super(ErrorCode.SOURCE_NOT_FOUND, message, "Make sure the archive contains src/main/java with at least one .java file.", cause);
    }
}
