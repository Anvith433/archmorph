package com.anvith.archmorph.common.exception;

public class ArchiveStorageException extends RuntimeException {

    public ArchiveStorageException(String message) {
        super(message);
    }

    public ArchiveStorageException(String message, Throwable cause) {
        super(message, cause);
    }

}