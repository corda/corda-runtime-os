package net.corda.ledger.utxo.flow.impl.flows.finality

import net.corda.v5.base.annotations.ConstructorForDeserialization
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction

@CordaSerializable
data class FinalityPayload @ConstructorForDeserialization constructor(val map: Map<String, Any?>) {
    private companion object {
        const val INITIAL_TRANSACTION = "INITIAL_TRANSACTION"
        const val WAIT_FOR_ADDITIONAL_SIGNATURES = "WAIT_FOR_ADDITIONAL_SIGNATURES"
    }
    constructor(initialTransaction: UtxoSignedTransaction, waitForAdditionalSignatures: Boolean) : this(
        mapOf(
            INITIAL_TRANSACTION to initialTransaction,
            WAIT_FOR_ADDITIONAL_SIGNATURES to waitForAdditionalSignatures
        )
    )
    val initialTransaction get() = map[INITIAL_TRANSACTION]
    val waitForAdditionalSignatures get() = map[WAIT_FOR_ADDITIONAL_SIGNATURES]
}