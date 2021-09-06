package net.corda.orm.impl

import net.corda.orm.DdlManage

class PostgresEntityManagerConfiguration(
    jdbcUrl: String,
    username: String,
    password: String,
    ddlManage: DdlManage = DdlManage.NONE,
    showSql: Boolean = false,
    formatSql: Boolean = false,
    isAutoCommit: Boolean = false,
    maximumPoolSize: Int = 10
) : DbEntityManagerConfiguration(
    "org.postgresql.Driver",
    jdbcUrl,
    username,
    password,
    ddlManage,
    showSql,
    formatSql,
    isAutoCommit,
    maximumPoolSize
)