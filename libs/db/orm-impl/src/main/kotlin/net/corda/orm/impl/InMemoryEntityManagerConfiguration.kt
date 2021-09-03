package net.corda.orm.impl

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import net.corda.orm.DdlManage
import net.corda.orm.EntityManagerConfiguration
import javax.sql.DataSource

class InMemoryEntityManagerConfiguration(dbName: String) : EntityManagerConfiguration {
    private val ds by lazy {
        val conf = HikariConfig()
        conf.driverClassName = "org.hsqldb.jdbc.JDBCDriver"
        conf.jdbcUrl = "jdbc:hsqldb:mem:$dbName"
        conf.username = "sa"
        conf.password = ""
        HikariDataSource(conf)
    }

    override val dataSource: DataSource
        get() = ds

    override val ddlManage: DdlManage
        get() = DdlManage.UPDATE
}
