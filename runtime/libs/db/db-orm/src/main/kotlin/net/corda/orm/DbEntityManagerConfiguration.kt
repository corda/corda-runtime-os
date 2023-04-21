package net.corda.orm

import net.corda.db.core.CloseableDataSource

class DbEntityManagerConfiguration(
    override val dataSource: CloseableDataSource,
    override val showSql: Boolean = false,
    override val formatSql: Boolean = false,
    override val ddlManage: DdlManage = DdlManage.NONE
) : EntityManagerConfiguration
