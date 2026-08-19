package com.anvith.archmorph.common.exception;

public class JavaParsingException extends RuntimeException {

    public JavaParsingException(String message) {
        super(message);
    }

    public JavaParsingException(String message,
                                Throwable cause) {
        super(message, cause);
    }

}