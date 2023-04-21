package net.corda.v5.base.exceptions;

import net.corda.v5.base.annotations.ConstructorForDeserialization;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;

/**
 * Base class for all exceptions used for runtime error conditions in Corda.
 * <p>
 * This is the exception class that is used to throw and handle all exceptions you could
 * encounter at runtime in a flow. This class and subclasses can be serialized by Corda
 * so are safe to throw in flows.
 */
public class CordaRuntimeException extends RuntimeException implements CordaThrowable {
    private String originalExceptionClassName;
    private String _message;
    private Throwable _cause;

    @SuppressWarnings("SameParameterValue")
    private static void requireNotNull(@Nullable Object obj, @NotNull String message) {
        if (obj == null) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * Constructor used to wrap any exception in a safe way, taking the original exception class name,
     * message and causes as parameters. This can wrap third party exceptions that cannot be serialized.
     */
    @ConstructorForDeserialization
    public CordaRuntimeException(
        @Nullable String originalExceptionClassName,
        @Nullable String message,
        @Nullable Throwable cause
    ) {
        super(null, null, true, true);
        this.originalExceptionClassName = originalExceptionClassName;
        this._message = message;
        this._cause = cause;
    }

    /**
     * Constructor with just a message and a cause, for rethrowing exceptions that can be serialized.
     */
    public CordaRuntimeException(@Nullable String message, @Nullable Throwable cause) {
        this(null, message, cause);
    }

    /**
     * Constructor with just a message (creating a fresh execption).
     */
    public CordaRuntimeException(@Nullable String message) {
        this(null, message, null);
    }

    @Override
    @Nullable
    public String getOriginalExceptionClassName() {
        return originalExceptionClassName;
    }

    @Override
    public void setOriginalExceptionClassName(@Nullable String originalExceptionClassName) {
        this.originalExceptionClassName = originalExceptionClassName;
    }

    @Override
    @Nullable
    public String getMessage() {
        final String originalMessage = getOriginalMessage();
        if (originalExceptionClassName == null) {
            return originalMessage;
        } else {
            if (originalMessage == null) {
                return originalExceptionClassName;
            } else {
                return originalExceptionClassName + ": " + originalMessage;
            }
        }
    }

    @Nullable
    public Throwable getCause() {
        return _cause == null ? super.getCause() : _cause;
    }

    @Override
    public void setMessage(@Nullable String message) {
        _message = message;
    }

    @Override
    public void setCause(@Nullable Throwable cause) {
        _cause = cause;
    }

    @Override
    public void addSuppressed(@NotNull Throwable[] suppressed) {
        requireNotNull(suppressed, "suppressed must not be null");
        for (Throwable suppress: suppressed) {
            addSuppressed(suppress);
        }
    }

    @Override
    @Nullable
    public String getOriginalMessage() {
        return _message;
    }

    @Override
    public int hashCode() {
        return Arrays.deepHashCode(getStackTrace()) ^ Objects.hash(originalExceptionClassName, getOriginalMessage());
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof CordaRuntimeException)) {
            return false;
        } else {
            final CordaRuntimeException other = (CordaRuntimeException) obj;
            return Objects.equals(originalExceptionClassName, other.originalExceptionClassName) &&
                    Objects.equals(getMessage(), other.getMessage()) &&
                    Objects.equals(getCause(), other.getCause()) &&
                    Arrays.equals(getStackTrace(), other.getStackTrace()) &&
                    Arrays.equals(getSuppressed(), other.getSuppressed());
        }
    }
}
