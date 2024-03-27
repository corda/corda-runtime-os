package net.corda.ledger.utxo.token.cache.queries


interface SqlQueryProvider {
    fun getBalanceQuery(includeTagFilter: Boolean, includeOwnerFilter: Boolean): String
    fun getPagedSelectQuery(limit: Int, includeTagFilter: Boolean, includeOwnerFilter: Boolean): String
}
