package net.corda.v5.ledger.notary.plugin.core;

import net.corda.v5.base.annotations.CordaSerializable;
import net.corda.v5.base.exceptions.CordaRuntimeException;
import net.corda.v5.crypto.SecureHash;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The base class for all notary errors that can be returned by the notary itself (plugin). Specific notary exceptions
 * must not inherit from this class directly, they should use one of the inner classes. To create a notary exception use
 * @see NotaryExceptionFatal
 * or 
 * @see NotaryExceptionUnknown
 */
@CordaSerializable
public abstract class NotaryException extends CordaRuntimeException {

    private final String notaryErrorMessage;
    private final SecureHash txId;

    /**
     * @return the specific error message produced by the notary.
     */
    @NotNull
    public final String getNotaryErrorMessage() {
        return this.notaryErrorMessage;
    }

    /**
     * @return txId Id of the transaction to be notarised. Can be _null_ if an error occurred before the id could be
     * resolved.
     */
    @Nullable
    public final SecureHash getTxId() {
        return this.txId;
    }

    NotaryException(@NotNull String notaryErrorMessage, @Nullable SecureHash txId) {
        super("Unable to notarise transaction " + (txId != null ? txId : "<Unknown>:") + " " + notaryErrorMessage);
        this.notaryErrorMessage = notaryErrorMessage;
        this.txId = txId;
    }
}
