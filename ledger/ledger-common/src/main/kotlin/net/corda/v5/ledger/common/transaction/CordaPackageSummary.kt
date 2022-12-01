package net.corda.v5.ledger.common.transaction

/**
 * Summary of the metadata of a corda package (CPK or CPI)
 */
interface CordaPackageSummary {
    /**
     * @property name Name of the package
     */
    val name: String

    /**
     * @property version Version of the pacakge
     */
    val version: String

    /**
     * @property signerSummaryHash hash sum identifying the signer for signed packages (CPIs). Null for CPKs
     */
    val signerSummaryHash: String?

    /**
     * @property fileChecksum checksum of the package file
     */
    val fileChecksum: String
}