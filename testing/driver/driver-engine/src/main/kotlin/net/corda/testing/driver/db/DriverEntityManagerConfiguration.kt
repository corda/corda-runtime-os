package net.corda.testing.driver.db

import net.corda.db.core.CloseableDataSource
import net.corda.orm.DdlManage
import net.corda.orm.EntityManagerConfiguration
import net.corda.orm.TransactionIsolationLevel

@Suppress("LongParameterList")
class DriverEntityManagerConfiguration(
    override val dataSource: CloseableDataSource,
    override val showSql: Boolean,
    override val formatSql: Boolean,
    override val jdbcTimezone: String,
    override val ddlManage: DdlManage,
    override val transactionIsolationLevel: TransactionIsolationLevel
) : EntityManagerConfiguration
