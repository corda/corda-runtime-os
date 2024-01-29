package net.corda.ledger.common.data.transaction.factory

import net.corda.ledger.common.data.transaction.PrivacySalt
import net.corda.ledger.common.data.transaction.WireTransaction

interface WireTransactionFactory {

    /**
     * Creates a [WireTransaction] from the passed in [componentGroupLists] and [privacySalt].
     *
     * This can be used when creating a new [WireTransaction] or recreating an existing [WireTransaction].
     *
     * @param componentGroupLists The component groups to include.
     * @param privacySalt An existing [PrivacySalt] to create the transaction with.
     *
     * @return A [WireTransaction].
     */
    fun create(
        componentGroupLists: List<List<ByteArray>>,
        privacySalt: PrivacySalt
    ): WireTransaction

    /**
     * Creates a [WireTransaction] from the passed in [componentGroupLists] and [privacySalt].
     *
     * This should be used when recreating an existing [WireTransaction]. Any missing component groups that exist in the transaction's
     * metadata are instantiated with an empty list.
     *
     * @param componentGroupLists The component groups to include.
     * @param privacySalt An existing [PrivacySalt] to create the transaction with.
     *
     * @return A [WireTransaction].
     */
    fun create(
        componentGroupLists: Map<Int, List<ByteArray>>,
        privacySalt: PrivacySalt
    ): WireTransaction
}
