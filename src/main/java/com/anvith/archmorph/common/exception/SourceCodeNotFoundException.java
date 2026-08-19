package com.anvith.archmorph.common.exception;

public class SourceCodeNotFoundException extends RuntimeException 
{

    public SourceCodeNotFoundException(String message) 
    {
        super(message);
    }

    public SourceCodeNotFoundException(String message, Throwable cause) 
    {
        super(message, cause);
    }
}