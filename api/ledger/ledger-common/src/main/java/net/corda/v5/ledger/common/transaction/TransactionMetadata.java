package net.corda.v5.ledger.common.transaction;

import net.corda.v5.base.annotations.DoNotImplement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * This interface represents the metadata governing a transaction, capturing a snapshot of the system and software versions as the
 * transaction was created.
 */
@DoNotImplement
public interface TransactionMetadata {

    /**
     * Gets the ledger model this transaction belongs to.
     *
     * @return The class name of the transaction implementation.
     */
    @NotNull
    String getLedgerModel();

    /**
     * Gets the version of the ledger this transaction was created with.
     *
     * @return The ledger version at creation time.
     */
    int getLedgerVersion();

    /**
     * Gets the transaction subtype. This is ledger specific, check with the documentation of the ledger model you are using.
     *
     * @return The transaction subtype as string defined in the ledger model.
     */
    @Nullable
    String getTransactionSubtype();

    /**
     * Gets the digest settings used to calculate the transaction hashes.
     *
     * @return The digest settings as map.
     */
    @NotNull
    Map<String, String> getDigestSettings();

    /**
     * Gets the platform version at the time of the creation of the transaction.
     *
     * @return Returns the platform version at the time of the creation of the transaction.
     */
    int getPlatformVersion();
}