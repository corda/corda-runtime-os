package net.corda.ledger.utxo.token.cache.queries

interface SqlQueryProvider {
    fun getPagedSelectQuery(limit: Int, includeTagFilter: Boolean, includeOwnerFilter: Boolean): String
}
