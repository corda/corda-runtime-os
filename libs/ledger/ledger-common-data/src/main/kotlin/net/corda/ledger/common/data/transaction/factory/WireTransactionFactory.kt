package net.corda.ledger.common.data.transaction.factory

import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.v5.ledger.common.transaction.PrivacySalt

interface WireTransactionFactory {

    /**
     * Creates a [WireTransaction] from the passed in [componentGroupLists].
     *
     * This should be used when constructing a new [WireTransaction].
     *
     * @param componentGroupLists The component groups to include.
     *
     * @return A [WireTransaction].
     */
    fun create(componentGroupLists: List<List<ByteArray>>): WireTransaction

    /**
     * Creates a [WireTransaction] from the passed in [componentGroupLists] and [privacySalt].
     *
     * This should be used when recreating an existing [WireTransaction].
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