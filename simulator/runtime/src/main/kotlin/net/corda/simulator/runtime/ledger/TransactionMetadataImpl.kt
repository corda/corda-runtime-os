package net.corda.simulator.runtime.ledger

import net.corda.v5.ledger.common.transaction.CordaPackageSummary
import net.corda.v5.ledger.common.transaction.TransactionMetadata

class TransactionMetadataImpl: TransactionMetadata {
    override fun getLedgerModel(): String {
        TODO("Not yet implemented")
    }

    override fun getLedgerVersion(): Int {
        TODO("Not yet implemented")
    }

    override fun getTransactionSubtype(): String? {
        TODO("Not yet implemented")
    }

    override fun getCpiMetadata(): CordaPackageSummary? {
        TODO("Not yet implemented")
    }

    override fun getCpkMetadata(): List<CordaPackageSummary> {
        TODO("Not yet implemented")
    }

    override fun getNumberOfComponentGroups(): Int {
        TODO("Not yet implemented")
    }

    override fun getDigestSettings(): LinkedHashMap<String, String> {
        TODO("Not yet implemented")
    }

    override fun getSchemaVersion(): Int {
        TODO("Not yet implemented")
    }
}