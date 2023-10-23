package net.corda.ledger.utxo.flow.impl.flows.finality

import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.v5.base.annotations.ConstructorForDeserialization
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction

@CordaSerializable
data class FinalityPayload @ConstructorForDeserialization constructor(val map: Map<String, Any?>) {
    private companion object {
        const val INITIAL_TRANSACTION = "INITIAL_TRANSACTION"
        const val TRANSFER_ADDITIONAL_SIGNATURES = "TRANSFER_ADDITIONAL_SIGNATURES"
        const val IS_NOTARY_BACKCHAIN_VERIFYING = "IS_NOTARY_BACKCHAIN_VERIFYING"
    }
    constructor(
        initialTransaction: UtxoSignedTransaction,
        transferAdditionalSignatures: Boolean,
        isNotaryBackchainVerifying: Boolean
    ) : this(
        mapOf(
            INITIAL_TRANSACTION to initialTransaction,
            TRANSFER_ADDITIONAL_SIGNATURES to transferAdditionalSignatures,
            IS_NOTARY_BACKCHAIN_VERIFYING to isNotaryBackchainVerifying
        )
    )
    val initialTransaction get() = map[INITIAL_TRANSACTION] as UtxoSignedTransactionInternal
    val transferAdditionalSignatures get() = map[TRANSFER_ADDITIONAL_SIGNATURES] as Boolean
    val isNotaryBackchainVerifying get() = map[IS_NOTARY_BACKCHAIN_VERIFYING] as Boolean
}