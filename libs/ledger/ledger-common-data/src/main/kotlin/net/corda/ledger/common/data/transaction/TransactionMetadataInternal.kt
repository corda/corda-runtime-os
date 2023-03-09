package net.corda.ledger.common.data.transaction

import net.corda.v5.ledger.common.transaction.CordaPackageSummary
import net.corda.v5.ledger.common.transaction.TransactionMetadata

interface TransactionMetadataInternal : TransactionMetadata {
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
     * Gets the version of the metadata JSON schema to parse this metadata entity.
     *
     * @return The schema version.
     */
    fun getSchemaVersion(): Int
}