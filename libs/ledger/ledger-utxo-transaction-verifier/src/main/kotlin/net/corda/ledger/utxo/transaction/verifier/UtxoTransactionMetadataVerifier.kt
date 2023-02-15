package net.corda.ledger.utxo.transaction.verifier

import net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionImpl
import net.corda.v5.ledger.common.transaction.TransactionMetadata

fun verifyMetadata(transactionMetadata: TransactionMetadata) {
    check(transactionMetadata.getLedgerModel() == UtxoLedgerTransactionImpl::class.java.name) {
        "The ledger model in the metadata of the transaction does not match with the expectation of the ledger. " +
                "'${transactionMetadata.getLedgerModel()}' != '${UtxoLedgerTransactionImpl::class.java.name}'"
    }
    checkNotNull(transactionMetadata.getTransactionSubtype()) {
        "The transaction subtype in the metadata of the transaction should not be empty for Utxo Transactions."
    }
}