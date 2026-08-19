package com.anvith.archmorph.common.exception;

public class ProjectExtractionException extends RuntimeException 
{

    public ProjectExtractionException(String message) 
    {
        super(message);
    }

    public ProjectExtractionException(String message, Throwable cause) 
    {
        super(message, cause);
    }

}