package com.anvith.archmorph.common.exception;

public class InvalidStateException extends ArchMorphException {

    public InvalidStateException(String message, String hint) {
        super(ErrorCode.INVALID_STATE, message, hint);
    }
}
