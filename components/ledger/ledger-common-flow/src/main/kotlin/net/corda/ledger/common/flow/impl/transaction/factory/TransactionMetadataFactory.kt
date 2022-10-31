package net.corda.ledger.common.flow.impl.transaction.factory

import net.corda.ledger.common.data.transaction.TransactionMetaData

interface TransactionMetadataFactory {
    fun create(ledgerSpecificMetadata: LinkedHashMap<String, String>): TransactionMetaData
}