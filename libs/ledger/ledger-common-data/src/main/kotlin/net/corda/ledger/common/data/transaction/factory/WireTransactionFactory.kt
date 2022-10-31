package net.corda.ledger.common.data.transaction.factory

import net.corda.ledger.common.data.transaction.TransactionBuilderInternal
import net.corda.ledger.common.data.transaction.TransactionMetaData
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.v5.ledger.common.transaction.PrivacySalt

interface WireTransactionFactory {
    fun create(
        transactionBuilderInternal: TransactionBuilderInternal,
        metadata: TransactionMetaData
    ): WireTransaction

    fun create(
        componentGroups: List<List<ByteArray>>,
        privacySalt: PrivacySalt
    ): WireTransaction
}