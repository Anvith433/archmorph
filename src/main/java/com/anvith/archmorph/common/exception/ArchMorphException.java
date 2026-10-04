package com.anvith.archmorph.common.exception;

/**
 * Base class of every expected ArchMorph failure.
 *
 * <p>The message must be safe to show to an end user: it may never contain
 * absolute paths, stack traces or internal class names. {@link #getHint()}
 * tells the user what they can do about it.</p>
 */
public class ArchMorphException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String hint;

    public ArchMorphException(ErrorCode errorCode, String message) {
        this(errorCode, message, null, null);
    }

    public ArchMorphException(ErrorCode errorCode, String message, String hint) {
        this(errorCode, message, hint, null);
    }

    public ArchMorphException(ErrorCode errorCode, String message, String hint, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.hint = hint;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public String getHint() {
        return hint;
    }
}
