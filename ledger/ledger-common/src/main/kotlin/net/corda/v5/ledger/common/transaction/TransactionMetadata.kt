package net.corda.v5.ledger.common.transaction

/**
 * This interface represents the metadata governing a transaction, capturing a snapshot of the system and software versions as the
 * transaction was created.
 */
interface TransactionMetadata {

    /**
     * Gets the ledger model this transaction belongs to.
     *
     * @return The class name of the transaction implementation.
     */
    fun getLedgerModel(): String

    /**
     * Gets the version of the ledger this transaction was created with.
     *
     * @return The ledger version at creation time.
     */
    fun getLedgerVersion(): Int

    /**
     * Gets the transaction subtype. This is ledger specific, check with the documentation of the ledger model you are using.
     *
     * @return The transaction subtype as string defined in the ledger model.
     */
    fun getTransactionSubtype(): String?

    /**
     * Gets information about the CPI running on the virtual node creating the transaction.
     *
     * @return A summary of the CPI.
     */
    fun getCpiMetadata(): CordaPackageSummary?

    /**
     * Gets information about the contract CPKs governing the transaction (installed on the virtual node when the transaction was created).
     *
     * @return A list of CPK summaries.
     */
    fun getCpkMetadata(): List<CordaPackageSummary>

    /**
     * Gets the number of the component groups included in the transaction.
     *
     * @return The number of component groups.
     */
    fun getNumberOfComponentGroups(): Int

    /**
     * Gets the digest settings used to calculate the transaction hashes.
     *
     * @return The digest settings as map.
     */
    fun getDigestSettings(): LinkedHashMap<String, Any>

    /**
     * Gets the version of the metadata JSON schema to parse this metadata entity.
     *
     * @return The schema version.
     */
    fun getSchemaVersion(): Int
}