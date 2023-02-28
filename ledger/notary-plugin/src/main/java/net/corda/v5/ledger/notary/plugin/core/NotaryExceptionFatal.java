package net.corda.v5.ledger.notary.plugin.core;

import net.corda.v5.base.annotations.CordaSerializable;
import net.corda.v5.crypto.SecureHash;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The base class for notary exceptions that we can guarantee were fatal and will result in the same failure every
 * time it is retried. These type of exceptions will invalidate the transaction immediately.
 */
@CordaSerializable
public abstract class NotaryExceptionFatal extends NotaryException {
    public NotaryExceptionFatal(@NotNull String notaryErrorMessage, @Nullable SecureHash txId) {
        super(notaryErrorMessage, txId);
    }
}
