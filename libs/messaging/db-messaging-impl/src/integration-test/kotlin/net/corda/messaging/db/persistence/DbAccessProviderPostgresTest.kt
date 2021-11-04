package net.corda.messaging.db.persistence

import net.corda.messaging.db.util.DbUtils.Companion.createOffsetsTableStmt
import net.corda.messaging.db.util.DbUtils.Companion.createTopicRecordsTableStmt
import net.corda.messaging.db.util.DbUtils.Companion.createTopicsTableStmt
import org.testcontainers.containers.PostgreSQLContainer
import java.lang.reflect.Type
import java.sql.DriverManager


//@Disabled("Disabled for CI until we have a shared database hosted by the infrastructure team. See INFRA-1485")
class DbAccessProviderPostgresTest: DbAccessProviderTestBase() {
    private val postgresqlServer = PostgreSQLContainer<Nothing>("postgres:9.6")

    override fun startDatabase() {
        postgresqlServer.start()
    }

    override fun stopDatabase() {
        postgresqlServer.stop()
    }

    override fun createTables() {
        val connection = DriverManager.getConnection(getJdbcUrl(), getUsername(), getPassword())
        connection.prepareStatement(createTopicRecordsTableStmt).execute()
        connection.prepareStatement(createOffsetsTableStmt).execute()
        connection.prepareStatement(createTopicsTableStmt).execute()
    }

    override fun getDbType(): DBType {
        return DBType.POSTGRESQL
    }

    override fun getJdbcUrl(): String {
        return postgresqlServer.jdbcUrl
    }

    override fun getUsername(): String {
        return postgresqlServer.username
    }

    override fun getPassword(): String {
        return postgresqlServer.password
    }

    override fun dbNullOrBlank(): Boolean {
        return System.getProperty("postgresqlServer").isNullOrBlank();
    }
}