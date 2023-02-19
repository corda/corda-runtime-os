package net.corda.simulator.runtime.ledger

import net.corda.v5.ledger.common.transaction.CordaPackageSummary
import net.corda.v5.ledger.common.transaction.TransactionMetadata

class SimTransactionMetadata: TransactionMetadata {
    override fun getCpiMetadata(): CordaPackageSummary? = null

    override fun getCpkMetadata(): List<CordaPackageSummary> = emptyList()

    override fun getDigestSettings(): Map<String, String> = emptyMap()

    override fun getLedgerModel(): String = ""

    override fun getLedgerVersion(): Int = 1

    override fun getNumberOfComponentGroups(): Int = 1
    override fun getPlatformVersion(): Int = 1

    override fun getSchemaVersion(): Int  = 1

    override fun getTransactionSubtype(): String? = null
}