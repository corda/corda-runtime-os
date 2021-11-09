package net.corda.messaging.db.persistence

import net.corda.messaging.db.util.DbUtils.Companion.createOffsetsTableStmt
import net.corda.messaging.db.util.DbUtils.Companion.createTopicRecordsTableStmt
import net.corda.messaging.db.util.DbUtils.Companion.createTopicsTableStmt
import java.sql.DriverManager

/*
To run locally:
- start your local postgres
- populate the variables (below) in gradle.properties with your local postgres values.
  postgresHost=
  postgresPort=
  postgresDb=
  postgresUser=
  postgresPassword=
 */

class DbAccessProviderPostgresTest: DbAccessProviderTestBase() {
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
        return "jdbc:postgresql://${System.getProperty("postgresHost")}:${System.getProperty("postgresPort")}/${System.getProperty("postgresDb")}"
    }

    override fun getUsername(): String {
        return System.getProperty("postgresUser")
    }

    override fun getPassword(): String {
        return System.getProperty("postgresPassword")
    }

    override fun startDatabase() {

    }

    override fun stopDatabase() {

    }
}