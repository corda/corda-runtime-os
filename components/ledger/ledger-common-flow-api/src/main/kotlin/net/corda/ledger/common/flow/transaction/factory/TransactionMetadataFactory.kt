package net.corda.ledger.common.flow.transaction.factory

import net.corda.ledger.common.data.transaction.TransactionMetadata

interface TransactionMetadataFactory {
    fun create(ledgerSpecificMetadata: Map<String, Any>): TransactionMetadata
}