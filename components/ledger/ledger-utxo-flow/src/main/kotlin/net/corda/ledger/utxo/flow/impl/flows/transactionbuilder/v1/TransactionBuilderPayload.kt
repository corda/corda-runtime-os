package net.corda.ledger.utxo.flow.impl.flows.transactionbuilder.v1

import net.corda.ledger.utxo.flow.impl.transaction.UtxoTransactionBuilderContainer
import net.corda.v5.base.annotations.ConstructorForDeserialization
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransactionAndSignatures

@CordaSerializable
data class TransactionBuilderPayload @ConstructorForDeserialization constructor(val map: Map<String, Any?>) {
    private companion object {
        const val TRANSACTION_BUILDER = "TRANSACTION_BUILDER"
        const val FILTERED_DEPENDENCIES = "FILTERED_DEPENDENCIES"
    }
    constructor(transactionBuilder: UtxoTransactionBuilderContainer) : this(
        mapOf(TRANSACTION_BUILDER to transactionBuilder)
    )

    constructor(
        transactionBuilder: UtxoTransactionBuilderContainer,
        filteredDependencies: List<UtxoFilteredTransactionAndSignatures>
    ) : this(
        mapOf(
            TRANSACTION_BUILDER to transactionBuilder,
            FILTERED_DEPENDENCIES to filteredDependencies
        )
    )

    val transactionBuilder get() = map[TRANSACTION_BUILDER] as UtxoTransactionBuilderContainer

    @Suppress("UNCHECKED_CAST")
    val filteredDependencies get() = map[FILTERED_DEPENDENCIES] as List<UtxoFilteredTransactionAndSignatures>?
}
