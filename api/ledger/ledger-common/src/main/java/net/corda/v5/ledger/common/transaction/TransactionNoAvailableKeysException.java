package net.corda.v5.ledger.common.transaction;

import net.corda.v5.base.exceptions.CordaRuntimeException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents the exception that is thrown when there are no keys available to sign a transaction.
 */
public final class TransactionNoAvailableKeysException extends CordaRuntimeException {

    /**
     * Creates a new instance of the TransactionNoAvailableKeysException class.
     *
     * @param message The details of the current exception to throw.
     * @param cause The underlying cause of the current exception.
     */
    public TransactionNoAvailableKeysException(
            @NotNull final String message,
            @Nullable final Throwable cause) {
        super(message, cause);
    }
}
