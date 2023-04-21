package net.corda.v5.crypto.exceptions;

import net.corda.v5.base.annotations.CordaSerializable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Signals that the signature verification has failed. The operation which caused the exception cannot be retried.
 */
@CordaSerializable
public final class CryptoSignatureException extends CryptoException {

    /**
     * Constructs a new exception with the specified detail message.
     *
     * @param message The detailed message.
     */
    public CryptoSignatureException(@NotNull String message) {
        super(message);
    }

    /**
     * Constructs a new exception with the specified detail message and cause.
     *
     * @param message The detailed message.
     * @param cause The cause.
     */
    public CryptoSignatureException(@NotNull String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}