package net.corda.v5.ledger.common.transaction;

import net.corda.v5.base.annotations.DoNotImplement;
import net.corda.v5.crypto.SecureHash;
import org.jetbrains.annotations.NotNull;

/**
 * Defines metadata properties of transactions common across difference ledger implementations.
 */
@DoNotImplement
public interface TransactionWithMetadata {

    /**
     * Gets the ID of the transaction.
     *
     * @return Returns the ID of the transaction.
     */
    @NotNull
    SecureHash getId();

    /**
     * Gets the metadata for the specified transaction.
     *
     * @return Returns the metadata for the specified transaction.
     */
    @NotNull
    TransactionMetadata getMetadata();
}
