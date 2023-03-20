package net.corda.ledger.persistence.query.execution.impl

import net.corda.v5.application.persistence.PagedQuery

// TODO need a common class but need to solve OSGi issues
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
