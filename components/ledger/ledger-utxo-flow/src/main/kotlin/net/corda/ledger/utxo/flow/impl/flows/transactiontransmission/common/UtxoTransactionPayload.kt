package net.corda.ledger.utxo.flow.impl.flows.transactiontransmission.common

import net.corda.v5.base.annotations.ConstructorForDeserialization
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransactionAndSignatures

@CordaSerializable
class UtxoTransactionPayload<T> @ConstructorForDeserialization constructor(val map: Map<String, Any?>) {
    private companion object {
        const val TRANSACTION = "TRANSACTION"
        const val FILTERED_DEPENDENCIES = "FILTERED_DEPENDENCIES"
    }
    constructor(transaction: T) : this(
        mapOf(TRANSACTION to transaction)
    )

    constructor(
        transaction: T,
        filteredDependencies: List<UtxoFilteredTransactionAndSignatures>
    ) : this(
        mapOf(
            TRANSACTION to transaction,
            FILTERED_DEPENDENCIES to filteredDependencies
        )
    )

    @Suppress("UNCHECKED_CAST")
    val transaction get() = map[TRANSACTION] as T?

    @Suppress("UNCHECKED_CAST")
    val filteredDependencies get() = map[FILTERED_DEPENDENCIES] as List<UtxoFilteredTransactionAndSignatures>?

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UtxoTransactionPayload<*>) return false

        if (map != other.map) return false

        return true
    }

    override fun hashCode(): Int {
        return map.hashCode()
    }
}
