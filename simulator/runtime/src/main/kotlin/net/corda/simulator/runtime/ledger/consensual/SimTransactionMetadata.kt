package net.corda.simulator.runtime.ledger.consensual

import net.corda.v5.ledger.common.transaction.TransactionMetadata

class SimTransactionMetadata: TransactionMetadata {
    override fun getDigestSettings(): Map<String, String> = emptyMap()

    override fun getLedgerModel(): String = ""

    override fun getLedgerVersion(): Int = 1

    override fun getPlatformVersion(): Int = 1

    override fun getTransactionSubtype(): String? = null
}