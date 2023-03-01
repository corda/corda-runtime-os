package net.corda.v5.ledger.common.transaction;

import net.corda.v5.base.exceptions.CordaRuntimeException;
import net.corda.v5.crypto.SecureHash;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Indicates a problem related to signatures of the transaction named by {@link #getTransactionId()}.
 */
public class TransactionSignatureException extends CordaRuntimeException {
    /**
     * The ID of the transaction that has failed the signature verification.
     */
    @NotNull
    private final SecureHash transactionId;

    /**
     * Creates a new instance of the TransactionSignatureException class.
     *
     * @param transactionId the Merkle root hash (identifier) of the transaction that failed verification.
     * @param message The details of the current exception to throw.
     * @param cause The underlying cause of the current exception.
     */
    public TransactionSignatureException(
            @NotNull SecureHash transactionId,
            @NotNull String message,
            @Nullable Throwable cause) {
        super(message, cause);
        this.transactionId = transactionId;
    }

    /**
     * Gets the ID of the transaction that has failed the signature verification.
     *
     * @return the Returns ID of the transaction that has failed the signature verification.
     */
    @NotNull
    public SecureHash getTransactionId() {
        return this.transactionId;
    }
}