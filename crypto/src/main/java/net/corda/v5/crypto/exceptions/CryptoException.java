package net.corda.v5.crypto.exceptions;

import net.corda.v5.base.annotations.CordaSerializable;
import net.corda.v5.base.exceptions.CordaRuntimeException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base exception for all Crypto Library specific exception. Note that the library may throw common exceptions
 * such as {@link IllegalArgumentException}, {@link IllegalStateException} and others as well. This base class is only
 * for the specific cases when a site throwing exception can provide some useful context about the operation.
 * <p>
 * Note that the approach for the Crypto Library is to use the existing exception where appropriate and use
 * the specific Crypto Library exceptions only to convey additional context about the conditions which lead to
 * the exception.
 */
@CordaSerializable
public class CryptoException extends CordaRuntimeException {
    /**
     * The flag specifying whenever the operation throwing the exception could be retried
     * without any intervention by application-level functionality.
     */
    private final boolean isRecoverable;

    /**
     * If the value is true then the error condition is considered transient and the operation which throws such
     * exceptions can be retried.
     */
    public final boolean isRecoverable() {
        return this.isRecoverable;
    }

    /**
     * Constructs a new exception with the specified detail message. The <code>isRecoverable</code> is set to false.
     *
     * @param message The detailed message.
     */
    public CryptoException(@NotNull String message) {
        super(message);
        this.isRecoverable = false;
    }

    /**
     * Constructs a new exception with the specified detail message and when it's recoverable.
     *
     * @param message       The detailed message.
     * @param isRecoverable The flag specifying whenever the operation throwing the exception could be retried
     *                      without any intervention by application-level functionality.
     */
    public CryptoException(@NotNull String message, boolean isRecoverable) {
        super(message);
        this.isRecoverable = isRecoverable;
    }

    /**
     * Constructs a new exception with the specified detail message and cause. The <code>isRecoverable</code>
     * is set to false.
     *
     * @param message The detailed message.
     * @param cause   The cause.
     */
    public CryptoException(@NotNull String message, @Nullable Throwable cause) {
        super(message, cause);
        this.isRecoverable = false;
    }

    /**
     * Constructs a new exception with the specified detail message, cause, and when it's recoverable.
     *
     * @param message       The detailed message.
     * @param isRecoverable The flag specifying whenever the operation throwing the exception could be retried
     *                      without any intervention by application-level functionality.
     * @param cause         The cause.
     */
    public CryptoException(@NotNull String message, boolean isRecoverable, @Nullable Throwable cause) {
        super(message, cause);
        this.isRecoverable = isRecoverable;
    }
}