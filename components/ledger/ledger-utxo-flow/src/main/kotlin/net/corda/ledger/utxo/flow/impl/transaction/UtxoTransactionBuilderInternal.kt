package net.corda.ledger.utxo.flow.impl.transaction

import net.corda.v5.ledger.utxo.transaction.UtxoTransactionBuilder

interface UtxoTransactionBuilderInternal : UtxoTransactionBuilder, UtxoTransactionBuilderData {
    /**
     * Returns another transaction builder with the same content.
     *
     * @return A copy of the current transaction builder.
     */
    fun copy(): UtxoTransactionBuilderContainer

    /**
     * Appends transaction builder components to a transaction builder.
     * It only appends the new components.
     * Also, notary and time window of the original takes precedence.
     * Those will not be overwritten regardless of if there are new values.
     *
     */
    fun append(other: UtxoTransactionBuilderContainer): UtxoTransactionBuilderInternal
}