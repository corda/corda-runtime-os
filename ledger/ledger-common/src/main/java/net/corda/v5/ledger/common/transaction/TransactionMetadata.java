package net.corda.v5.ledger.common.transaction;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * This interface represents the metadata governing a transaction, capturing a snapshot of the system and software versions as the
 * transaction was created.
 */
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
     * Gets information about the CPI running on the virtual node creating the transaction.
     *
     * @return A summary of the CPI.
     */
    @Nullable
    CordaPackageSummary getCpiMetadata();

    /**
     * Gets information about the contract CPKs governing the transaction (installed on the virtual node when the transaction was created).
     *
     * @return A list of CPK summaries.
     */
    @NotNull
    List<CordaPackageSummary> getCpkMetadata();

    /**
     * Gets the number of the component groups included in the transaction.
     *
     * @return The number of component groups.
     */
    int getNumberOfComponentGroups();

    /**
     * Gets the digest settings used to calculate the transaction hashes.
     *
     * @return The digest settings as map.
     */
    @NotNull
    Map<String, String> getDigestSettings();

    /**
     * Gets the version of the metadata JSON schema to parse this metadata entity.
     *
     * @return The schema version.
     */
    int getSchemaVersion();

    /**
     * Gets the platform version at the time of the creation of the transaction.
     *
     * @return Returns the platform version at the time of the creation of the transaction.
     */
    int getPlatformVersion();
}