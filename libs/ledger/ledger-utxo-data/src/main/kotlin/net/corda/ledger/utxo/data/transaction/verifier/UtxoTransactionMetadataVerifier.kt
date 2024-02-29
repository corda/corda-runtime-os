package net.corda.ledger.utxo.data.transaction.verifier

import net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionImpl
import net.corda.v5.ledger.common.transaction.TransactionMetadata

fun verifyMetadata(transactionMetadata: TransactionMetadata) {
    // Check the ledger model key, typically set up when the user flow built the transaction,
    // specified in [UtxoSignedTransactionFactoryImpl.utxoMetadata] then populated in the transaction
    // metadata map in [TransactionMetadataFactoryImpl.create]

    // [transaction.getLedgerModel] is simply a convenience method for accessing the LEDGER_MODEL_KEY
    // field of the transaction metadata properties Map<String,Any>
    check(transactionMetadata.getLedgerModel() == UtxoLedgerTransactionImpl::class.java.name) {
        "The ledger model in the metadata of the transaction does not match with the expectation of the ledger. " +
            "'${transactionMetadata.getLedgerModel()}' != '${UtxoLedgerTransactionImpl::class.java.name}'"
    }

    // [transaction.getTransactionSubtype] looks up TRANSACTION_SUBTYPE_KEY, and calls toString() on whatever it finds.
    // TRANSACTION_SUBTYPE_KEY is set in [UtxoSignedTransactionFactoryImpl.utxoMetadata] to [TransactionSubtype.General].
    // but we'll accept TransactionSubtype.NOTARY_CHANGE even though we have no code which sets that up.
    checkNotNull(transactionMetadata.getTransactionSubtype()) {
        "The transaction subtype in the metadata of the transaction should not be empty for Utxo Transactions."
    }
}
