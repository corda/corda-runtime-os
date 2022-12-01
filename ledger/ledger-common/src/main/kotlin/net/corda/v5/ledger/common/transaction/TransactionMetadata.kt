package net.corda.v5.ledger.common.transaction

/**
 * This interface represents the metadata governing a transaction, capturing a snapshot
 * of the system and software versions as the transaction was created.
 */
interface TransactionMetadata {

    /**
     * Returns the ledger model this transaction belongs to.
     *
     * @return the class name of the transaction implementation
     */
    fun getLedgerModel(): String

    /**
     * Returns the version of the ledger this transaction was created with
     *
     * @return the ledger version at creation time
     */
    fun getLedgerVersion(): Int

    /**
     * Returns the transaction subtype. This is ledger specific, check with the documentation of the
     * ledger model you are using.
     *
     * @return Transaction subtype as string defined in the ledger model
     */
    fun getTransactionSubtype(): String?

    /**
     * Get information about the CPI running on the virtual node creating the transaction
     *
     * @return a summary of the CPI
     */
    fun getCpiMetadata(): CordaPackageSummary?

    /**
     * Get information about the contract CPKs governing the transaction (installed on the
     * virtual node when the transaction was created)
     *
     * @return list of CPK summaries
     */
    fun getCpkMetadata(): List<CordaPackageSummary>

    /**
     * Get the digest settings used to calculate the transaction hashes
     *
     * @return digest settings as map
     */
    fun getDigestSettings(): LinkedHashMap<String, Any>

    /**
     * Version of the metadata JSON schema to parse this metadata entity
     *
     * @return The schema version
     */
    fun getSchemaVersion(): Int
}