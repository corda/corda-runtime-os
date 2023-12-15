package net.corda.ledger.utxo.flow.impl.transaction.factory

import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionInternal
import net.corda.ledger.utxo.data.transaction.UtxoVisibleTransactionOutputDto
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction

/**
 * Factory to create a [UtxoLedgerTransaction]s.
 * This is required to resolve input and reference stateRefs to actual
 * transaction states.
 */
interface UtxoLedgerTransactionFactory {

    /**
     * Resolves the input and reference stateRefs to TransactionState objects
     * and returns a fully resolved UtxoLedgerTransation
     */
    @Suspendable
    fun create(
        wireTransaction: WireTransaction
    ): UtxoLedgerTransactionInternal

    fun create(
        wireTransaction: WireTransaction,
        inputStateAndRefs: List<UtxoVisibleTransactionOutputDto>,
        referenceStateAndRefs: List<UtxoVisibleTransactionOutputDto>
    ): UtxoLedgerTransactionInternal

    fun createWithStateAndRefs(
        wireTransaction: WireTransaction,
        inputStateAndRefs: List<StateAndRef<*>>,
        referenceStateAndRefs: List<StateAndRef<*>>
    ): UtxoLedgerTransactionInternal
}
