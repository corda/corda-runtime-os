package net.corda.ledger.utxo.flow.impl.persistence

import net.corda.v5.application.persistence.PagedQuery

// TODO Should we have a common `PagedQuery.ResultSet` implementation?
data class LedgerResultSetImpl<R>(
    private val results: List<R>
) : PagedQuery.ResultSet<R> {
    override fun getResults() = results
}
