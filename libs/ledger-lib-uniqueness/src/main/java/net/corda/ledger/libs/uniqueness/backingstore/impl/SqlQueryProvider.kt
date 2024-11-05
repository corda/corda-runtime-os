package net.corda.ledger.libs.uniqueness.backingstore.impl

interface SqlQueryProvider {
    fun findStatesByKeyQuery(): String
    fun findTransactionDetailByKeyQuery(): String
    fun findRejectedTransactionQuery(): String
    fun insertUnconsumedStatesQuery(): String
    fun consumeStatesQuery(): String
    fun insertTransactionDetailsQuery(): String
    fun insertRejectedTransactionQuery(): String
}
