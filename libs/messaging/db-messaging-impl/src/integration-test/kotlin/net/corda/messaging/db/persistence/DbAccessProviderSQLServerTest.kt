package net.corda.messaging.db.persistence

import net.corda.messaging.db.util.DbUtils
import org.junit.jupiter.api.Disabled
import org.testcontainers.containers.MSSQLServerContainer
import java.sql.DriverManager

@Disabled("Disabled for CI until we have a shared database hosted by the infrastructure team. See INFRA-1485")
class DbAccessProviderSQLServerTest: DbAccessProviderTestBase() {

    private val sqlServer = MSSQLServerContainer<Nothing>("mcr.microsoft.com/mssql/server:2017-latest").apply { this.acceptLicense() }

    override fun startDatabase() {
        sqlServer.start()
    }

    override fun stopDatabase() {
        sqlServer.stop()
    }

    override fun createTables() {
        val connection = DriverManager.getConnection(getJdbcUrl(), getUsername(), getPassword())
        connection.prepareStatement(DbUtils.SQLServer.createTopicRecordsTableStmt).execute()
        connection.prepareStatement(DbUtils.SQLServer.createOffsetsTableStmt).execute()
        connection.prepareStatement(DbUtils.SQLServer.createTopicsTableStmt).execute()
    }

    override fun getDbType(): DBType {
        return DBType.SQL_SERVER
    }

    override fun getJdbcUrl(): String {
        return sqlServer.jdbcUrl
    }

    override fun getUsername(): String {
        return sqlServer.username
    }

    override fun getPassword(): String {
        return sqlServer.password
    }

    override fun hasDbConfigured(): Boolean {
        return true
    }
}