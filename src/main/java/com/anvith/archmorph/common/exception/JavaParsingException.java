package com.anvith.archmorph.common.exception;

public class JavaParsingException extends ArchMorphException {

    public JavaParsingException(String message) {
        super(ErrorCode.JAVA_PARSE_ERROR, message, null);
    }

    public JavaParsingException(String message, Throwable cause) {
        super(ErrorCode.JAVA_PARSE_ERROR, message, null, cause);
    }
}
