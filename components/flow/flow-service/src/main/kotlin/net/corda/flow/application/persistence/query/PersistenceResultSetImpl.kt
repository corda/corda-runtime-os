package net.corda.flow.application.persistence.query

import net.corda.v5.application.persistence.PagedQuery

data class PersistenceResultSetImpl<R>(
    private val results: List<R>,
    private val size: Int,
    private val newOffset: Int,
    private val hasNextPage: Boolean
) : PagedQuery.ResultSet<R> {
    override fun getNewOffset() = newOffset

    override fun getSize() = size

    override fun hasNextPage() = hasNextPage

    override fun getResults() = results
}
