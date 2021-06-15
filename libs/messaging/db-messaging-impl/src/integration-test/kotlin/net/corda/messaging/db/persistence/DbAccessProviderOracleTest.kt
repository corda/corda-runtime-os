package net.corda.messaging.db.persistence

import net.corda.messaging.db.util.DbUtils
import org.testcontainers.containers.OracleContainer
import java.sql.DriverManager


class DbAccessProviderOracleTest: DbAccessProviderTestBase() {

    private val oracleServer = OracleContainer("oracleinanutshell/oracle-xe-11g")

    override fun startDatabase() {
        oracleServer.start()
    }

    override fun stopDatabase() {
        oracleServer.stop()
    }

    override fun createTables() {
        val connection = DriverManager.getConnection(getJdbcUrl(), getUsername(), getPassword())
        connection.prepareStatement(DbUtils.Oracle.createTopicRecordsTableStmt).execute()
        connection.prepareStatement(DbUtils.Oracle.createOffsetsTableStmt).execute()
        connection.prepareStatement(DbUtils.Oracle.createTopicsTableStmt).execute()
    }

    override fun getDbType(): DBType {
        return DBType.ORACLE
    }

    override fun getJdbcUrl(): String {
        return oracleServer.jdbcUrl
    }

    override fun getUsername(): String {
        return oracleServer.username
    }

    override fun getPassword(): String {
        return oracleServer.password
    }
}