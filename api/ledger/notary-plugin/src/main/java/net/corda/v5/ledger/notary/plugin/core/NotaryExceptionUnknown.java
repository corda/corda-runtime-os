package net.corda.v5.ledger.notary.plugin.core;

import net.corda.v5.base.annotations.CordaSerializable;
import net.corda.v5.crypto.SecureHash;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The base class for notary exceptions that we cannot guarantee were fatal and might return with a different result
 * when retried. These type of exceptions will not invalidate the transaction immediately.
 */
@CordaSerializable
public abstract class NotaryExceptionUnknown extends NotaryException {
    public NotaryExceptionUnknown(@NotNull String notaryErrorMessage, @Nullable SecureHash txId) {
        super(notaryErrorMessage, txId);
    }
}
