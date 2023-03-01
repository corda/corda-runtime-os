package net.corda.v5.ledger.common.transaction;

import net.corda.v5.base.exceptions.CordaRuntimeException;
import net.corda.v5.crypto.SecureHash;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents the exception that is thrown to indicate that some aspect of the transaction has violated some rules.
 */
public final class TransactionVerificationException extends CordaRuntimeException {

    /**
     * The ID of the transaction that has failed verification.
     */
    @NotNull
    private final SecureHash transactionId;

    public TransactionVerificationException(
            @NotNull final SecureHash transactionId,
            @NotNull final String message,
            @Nullable final Throwable cause) {
        super(message, cause);
        this.transactionId = transactionId;
    }

    /**
     * Gets the ID of the transaction that has failed verification.
     *
     * @return the Returns ID of the transaction that has failed verification.
     */
    @NotNull
    public SecureHash getTransactionId() {
        return this.transactionId;
    }
}
