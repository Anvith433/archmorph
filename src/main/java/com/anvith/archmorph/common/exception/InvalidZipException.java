package com.anvith.archmorph.common.exception;

public class InvalidZipException extends ArchMorphException {

    public InvalidZipException(String message) {
        super(ErrorCode.INVALID_ARCHIVE, message, "Upload a valid .zip archive of your project.");
    }

    public InvalidZipException(ErrorCode code, String message, String hint) {
        super(code, message, hint);
    }
}
