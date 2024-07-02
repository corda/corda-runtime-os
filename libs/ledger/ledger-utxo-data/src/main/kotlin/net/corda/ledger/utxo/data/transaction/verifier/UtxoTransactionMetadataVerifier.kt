package net.corda.ledger.utxo.data.transaction.verifier

import net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionImpl
import net.corda.v5.ledger.common.transaction.TransactionMetadata

fun verifyMetadata(transactionMetadata: TransactionMetadata) {
    // Since Corda support multiple types of ledger, with different implementations, we need to check
    // that the data field indicating the ledger type matches UTXO.

    check(transactionMetadata.getLedgerModel() == UtxoLedgerTransactionImpl::class.java.name) {
        "The ledger model in the metadata of the transaction does not match with the expectation of the ledger. " +
            "'${transactionMetadata.getLedgerModel()}' != '${UtxoLedgerTransactionImpl::class.java.name}'"
    }

    checkNotNull(transactionMetadata.getTransactionSubtype()) {
        "The transaction subtype in the metadata of the transaction should not be empty for Utxo Transactions."
    }
}
