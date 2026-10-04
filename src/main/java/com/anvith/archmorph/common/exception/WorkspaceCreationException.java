package com.anvith.archmorph.common.exception;

public class WorkspaceCreationException extends ArchMorphException {

    public WorkspaceCreationException(String message) {
        super(ErrorCode.WORKSPACE_ERROR, message, null);
    }

    public WorkspaceCreationException(String message, Throwable cause) {
        super(ErrorCode.WORKSPACE_ERROR, message, null, cause);
    }
}
