package net.corda.ledger.utxo.token.cache.queries

import net.corda.v5.ledger.utxo.token.selection.Strategy

interface SqlQueryProvider {
    fun getBalanceQuery(includeTagFilter: Boolean, includeOwnerFilter: Boolean): String
    fun getPagedSelectQuery(limit: Int, includeTagFilter: Boolean, includeOwnerFilter: Boolean, strategy: Strategy): String
}
