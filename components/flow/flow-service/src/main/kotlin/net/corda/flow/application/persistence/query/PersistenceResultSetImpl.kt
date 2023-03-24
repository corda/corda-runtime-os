package net.corda.flow.application.persistence.query

import net.corda.v5.application.persistence.PagedQuery

data class PersistenceResultSetImpl<R>(
    private val results: List<R>
) : PagedQuery.ResultSet<R> {
    override fun getResults() = results
}
