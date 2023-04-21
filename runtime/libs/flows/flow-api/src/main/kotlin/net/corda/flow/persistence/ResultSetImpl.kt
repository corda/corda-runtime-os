package net.corda.flow.persistence

import net.corda.v5.application.persistence.PagedQuery

data class ResultSetImpl<R>(
    private val results: List<R>
) : PagedQuery.ResultSet<R> {
    override fun getResults() = results
}
