package com.anvith.archmorph.common.exception;

public class InvalidProjectStructureException extends RuntimeException {

    public InvalidProjectStructureException(String message) {
        super(message);
    }

    public InvalidProjectStructureException(String message, Throwable cause) {
        super(message, cause);
    }

}