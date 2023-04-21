package net.corda.ledger.common.flow.transaction.filtered.factory

import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.flow.transaction.filtered.FilteredTransaction
import net.corda.v5.base.annotations.Suspendable
import java.util.function.Predicate

/**
 * [FilteredTransactionFactory] creates [FilteredTransaction] instances from [WireTransaction]s.
 */
interface FilteredTransactionFactory {

    /**
     * Creates a [FilteredTransaction] from a [WireTransaction] by applying a filtering [Predicate] to every item in
     * every component group contained in the transaction.
     *
     * @param wireTransaction The [WireTransaction] to filter and convert to a [FilteredTransaction].
     * @param componentGroupFilterParameters The ordinals to include in the filtered transaction along with the deserialized form of each
     * component and the type of Merkle proof that should be created.
     *
     * @return A [FilteredTransaction] only containing items that passed through the [filter].
     */
    @Suspendable
    fun create(
        wireTransaction: WireTransaction,
        componentGroupFilterParameters: List<ComponentGroupFilterParameters>
    ): FilteredTransaction
}