package net.corda.ledger.common.flow.transaction.factory

import net.corda.v5.ledger.common.transaction.TransactionMetadata

interface TransactionMetadataFactory {
    fun create(ledgerSpecificMetadata: Map<String, Any>): TransactionMetadata
}