package com.anvith.archmorph.common.exception;

public class ArchiveStorageException extends ArchMorphException {

    public ArchiveStorageException(String message) {
        super(ErrorCode.STORAGE_ERROR, message, null);
    }

    public ArchiveStorageException(String message, Throwable cause) {
        super(ErrorCode.STORAGE_ERROR, message, null, cause);
    }
}
