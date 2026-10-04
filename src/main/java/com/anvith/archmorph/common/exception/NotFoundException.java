package com.anvith.archmorph.common.exception;

public class NotFoundException extends ArchMorphException {

    public NotFoundException(ErrorCode code, String message) {
        super(code, message, null);
    }
}
