package net.corda.orm

import javax.sql.DataSource

class DbEntityManagerConfiguration(
    override val dataSource: DataSource,
    override val showSql: Boolean = false,
    override val formatSql: Boolean = false,
    override val ddlManage: DdlManage = DdlManage.NONE
) : EntityManagerConfiguration
