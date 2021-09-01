package net.corda.orm

import javax.sql.DataSource

/**
 * Entity manager configuration
 *
 */
interface EntityManagerConfiguration {
    val dataSource: DataSource
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