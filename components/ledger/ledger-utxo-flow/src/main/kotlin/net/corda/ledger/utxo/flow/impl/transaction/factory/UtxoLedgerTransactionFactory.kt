package net.corda.ledger.utxo.flow.impl.transaction.factory

import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionInternal
import net.corda.ledger.utxo.data.transaction.UtxoTransactionOutputDto
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import net.corda.v5.membership.GroupParameters

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
    ): UtxoLedgerTransaction

    fun create(
        wireTransaction: WireTransaction,
        inputStateAndRefs: List<UtxoTransactionOutputDto>,
        referenceStateAndRefs: List<UtxoTransactionOutputDto>
    ): UtxoLedgerTransactionInternal

    fun getGroupParameters(wireTransaction: WireTransaction): GroupParameters
}
