package com.anvith.archmorph.common.exception;

public class ProjectExtractionException extends ArchMorphException {

    public ProjectExtractionException(String message) {
        super(ErrorCode.EXTRACTION_ERROR, message, null);
    }

    public ProjectExtractionException(String message, Throwable cause) {
        super(ErrorCode.EXTRACTION_ERROR, message, null, cause);
    }
}
