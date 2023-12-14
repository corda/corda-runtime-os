package net.corda.ledger.common.data.transaction

import com.fasterxml.jackson.annotation.JsonIgnore
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
     * Gets the component group structure included in the transaction.
     * For example:
     * [
     *      ["metadata"],
     *      [
     *          "net.corda.v5.base.types.MemberX500Name",
     *          "java.security.PublicKey",
     *          "net.corda.v5.ledger.utxo.TimeWindow"
     *      ],
     *      ["java.security.PublicKey"],
     *      ["net.corda.ledger.utxo.data.transaction.UtxoOutputInfoComponent"],
     *      ["CommandInfo"],
     *      ["net.corda.v5.crypto.SecureHash"],
     *      ["net.corda.v5.ledger.utxo.StateRef"],
     *      ["net.corda.v5.ledger.utxo.StateRef"],
     *      ["net.corda.v5.ledger.utxo.ContractState"],
     *      ["net.corda.v5.ledger.utxo.Command"]
     * ]
     *
     * @return The component group structure
     */
    fun getComponentGroups(): List<List<String>>

    /**
     * Gets the number of the component groups included in the transaction.
     *
     * @return The number of component groups.
     */
    @JsonIgnore
    fun getNumberOfComponentGroups(): Int

    /**
     * Gets the version of the metadata JSON schema to parse this metadata entity.
     *
     * @return The schema version.
     */
    fun getSchemaVersion(): Int

    /**
     * Gets the hash of the membership group parameters.
     *
     * @return Membership group parameters hash
     */
    fun getMembershipGroupParametersHash(): String?
}
