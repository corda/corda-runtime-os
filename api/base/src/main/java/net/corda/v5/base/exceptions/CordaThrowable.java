package net.corda.v5.base.exceptions;

import net.corda.v5.base.annotations.CordaSerializable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base interface for exceptions that are serializable in Corda. Do not use directly, use {@link CordaRuntimeException}
 * instead.
 */
@CordaSerializable
public interface CordaThrowable {
    /**
     * @return The name of an exception that isn't serializable and therefore has been caught
     * and converted to an exception in the Corda hierarchy.
     */
    @Nullable
    String getOriginalExceptionClassName();

    void setOriginalExceptionClassName(@Nullable String originalExceptionClassName);

    /**
     * @return Message of the original exception
     */
    @Nullable
    String getOriginalMessage();

    /**
     * Allows to set the message after constructing the exception object.
     */
    void setMessage(@Nullable String message);

    /**
     * Allows to set a Throwable as cause after constructing the exception object.
     */
    void setCause(@Nullable Throwable cause);

    void addSuppressed(@NotNull Throwable[] suppressed);
}