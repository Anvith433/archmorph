package com.anvith.archmorph.common.exception;

public class WorkspaceCreationException extends RuntimeException 
{

    public WorkspaceCreationException(String message) 
    {
        super(message);
    }

    public WorkspaceCreationException(String message, Throwable cause) 
    {
        super(message, cause);
    }

}