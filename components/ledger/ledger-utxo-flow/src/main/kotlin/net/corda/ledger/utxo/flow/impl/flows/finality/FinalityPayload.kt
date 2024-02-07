package net.corda.ledger.utxo.flow.impl.flows.finality

import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.v5.base.annotations.ConstructorForDeserialization
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransactionAndSignatures

@CordaSerializable
data class FinalityPayload @ConstructorForDeserialization constructor(val map: Map<String, Any?>) {
    private companion object {
        const val INITIAL_TRANSACTION = "INITIAL_TRANSACTION"
        const val TRANSFER_ADDITIONAL_SIGNATURES = "TRANSFER_ADDITIONAL_SIGNATURES"
        const val FILTERED_TRANSACTIONS_AND_SIGNATURES = "FILTERED_TRANSACTIONS_AND_SIGNATURES"
    }
    constructor(
        initialTransaction: UtxoSignedTransaction,
        transferAdditionalSignatures: Boolean,
        filteredTransactionsAndSignatures: List<UtxoFilteredTransactionAndSignatures>? = null
    ) : this(
        mapOf(
            INITIAL_TRANSACTION to initialTransaction,
            TRANSFER_ADDITIONAL_SIGNATURES to transferAdditionalSignatures,
            FILTERED_TRANSACTIONS_AND_SIGNATURES to filteredTransactionsAndSignatures
        )
    )

    val initialTransaction get() = map[INITIAL_TRANSACTION] as UtxoSignedTransactionInternal
    val transferAdditionalSignatures get() = map[TRANSFER_ADDITIONAL_SIGNATURES] as Boolean

    @Suppress("UNCHECKED_CAST")
    val filteredTransactionsAndSignatures get() = map[FILTERED_TRANSACTIONS_AND_SIGNATURES] as List<UtxoFilteredTransactionAndSignatures>?
}
