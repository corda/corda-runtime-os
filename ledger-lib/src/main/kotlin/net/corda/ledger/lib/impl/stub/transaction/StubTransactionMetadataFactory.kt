package net.corda.ledger.lib.impl.stub.transaction

import net.corda.ledger.common.data.transaction.TransactionMetadataImpl
import net.corda.ledger.common.flow.transaction.factory.TransactionMetadataFactory
import net.corda.v5.ledger.common.transaction.TransactionMetadata

class StubTransactionMetadataFactory : TransactionMetadataFactory {
    override fun create(ledgerSpecificMetadata: Map<String, Any>): TransactionMetadata {
        return TransactionMetadataImpl(mapOf(
            "membershipGroupParametersHash" to "membershipGroupParametersHash",
            "cpiMetadata" to mapOf(
                "name" to "name",
                "version" to "version",
                "signerSummaryHash" to "signerSummaryHash",
                "fileChecksum" to "cpiFileChecksum"
            )
        ))
    }
}