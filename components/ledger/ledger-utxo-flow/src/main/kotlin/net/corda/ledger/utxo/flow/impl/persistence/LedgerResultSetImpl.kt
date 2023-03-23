package net.corda.ledger.utxo.flow.impl.persistence

import net.corda.v5.application.persistence.PagedQuery

// TODO Should we have a common `PagedQuery.ResultSet` implementation?
data class LedgerResultSetImpl<R>(
    private val newOffset: Int,
    private val size: Int,
    private val hasNextPage: Boolean,
    private val results: List<R>
) : PagedQuery.ResultSet<R> {
    override fun getNewOffset() = newOffset

    override fun getSize() = size

    override fun hasNextPage() = hasNextPage

    override fun getResults() = results
}
