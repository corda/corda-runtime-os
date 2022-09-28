package net.corda.ledger.utxo.impl.transaction.factory

import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.data.transaction.filtered.FilteredTransaction
import net.corda.v5.base.annotations.Suspendable
import java.util.function.Predicate

interface UtxoFilteredTransactionFactory {

    /**
     * Creates a [FilteredTransaction] from a [WireTransaction] by applying a filtering [Predicate] to every item in every component group
     * contained in the transaction.
     *
     * @param wireTransaction The [WireTransaction] to filter and convert to a [FilteredTransaction].
     * @param filter The [Predicate] to apply to each item in the transaction.
     *
     * @return A [FilteredTransaction] only containing items that passed through the [filter].
     */
    @Suspendable
    fun create(wireTransaction: WireTransaction, filter: Predicate<Any>): FilteredTransaction
}