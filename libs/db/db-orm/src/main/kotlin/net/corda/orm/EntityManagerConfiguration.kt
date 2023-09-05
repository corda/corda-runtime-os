package net.corda.orm

import net.corda.db.core.CloseableDataSource

/**
 * Entity manager configuration
 *
 */
interface EntityManagerConfiguration {
    fun close() {
        dataSource.close()
    }

    val dataSource: CloseableDataSource
    val showSql: Boolean
        get() = false
    val formatSql: Boolean
        get() = false
    val transactionIsolationLevel: TransactionIsolationLevel
        get() = TransactionIsolationLevel.default
    val ddlManage: DdlManage
        get() = DdlManage.NONE
    val jdbcTimezone: String
        get() = "UTC"
}