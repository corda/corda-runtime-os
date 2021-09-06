package net.corda.orm.impl

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import net.corda.orm.DdlManage

@Suppress("LongParameterList")
class PostgresEntityManagerConfiguration(
    jdbcUrl: String,
    username: String,
    password: String,
    ddlManage: DdlManage = DdlManage.NONE,
    showSql: Boolean = false,
    formatSql: Boolean = false,
    isAutoCommit: Boolean = false,
    maximumPoolSize: Int = 10,
    hikariDataSourceFactory: (c: HikariConfig) -> HikariDataSource = { c ->
        HikariDataSource(c)
    }
) : DbEntityManagerConfiguration(
    "org.postgresql.Driver",
    jdbcUrl,
    username,
    password,
    ddlManage,
    showSql,
    formatSql,
    isAutoCommit,
    maximumPoolSize,
    hikariDataSourceFactory
)