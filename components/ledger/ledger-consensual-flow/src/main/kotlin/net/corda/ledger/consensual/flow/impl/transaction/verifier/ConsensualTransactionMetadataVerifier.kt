package net.corda.ledger.consensual.flow.impl.transaction.verifier

import net.corda.ledger.consensual.data.transaction.ConsensualLedgerTransactionImpl
import net.corda.v5.ledger.common.transaction.TransactionMetadata

fun verifyMetadata(transactionMetadata: TransactionMetadata) {
    check(transactionMetadata.getLedgerModel() == ConsensualLedgerTransactionImpl::class.java.name) {
        "The ledger model in the metadata of the transaction does not match with the expectation of the ledger. " +
                "'${transactionMetadata.getLedgerModel()}' != '${ConsensualLedgerTransactionImpl::class.java.name}'"
    }
    check(transactionMetadata.getTransactionSubtype() == null) {
        "The transaction subtype in the metadata of the transaction should be empty for Consensual Transactions."
    }
}