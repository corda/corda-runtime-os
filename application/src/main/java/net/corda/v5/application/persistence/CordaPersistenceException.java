package net.corda.v5.application.persistence;

import net.corda.v5.base.annotations.ConstructorForDeserialization;
import net.corda.v5.base.exceptions.CordaRuntimeException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Exception that encapsulates errors during persistence.
 */
public final class CordaPersistenceException extends CordaRuntimeException {

    /**
     * @param message The exception message.
     * @param cause Optional throwable that was caught.
     */
    @ConstructorForDeserialization
    public CordaPersistenceException(@NotNull String message, @Nullable Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a {@link CordaPersistenceException} without a {@code cause}.
     *
     * @param message The exception message.
     */
    public CordaPersistenceException(@NotNull String message) {
        this(message, null);
    }
}
