package net.corda.db.testkit.dbutilsimpl

import java.sql.Connection

class PostgresHelper : AbstractDBHelper() {
    companion object {
        private const val POSTGRES_PORT_PROPERTY = "postgresPort"
        private const val POSTGRES_HOST_PROPERTY = "postgresHost"
    }

    override fun isInMemory() = false

    override fun getDatabase() = getPropertyNonBlank("postgresDb","postgres")

    override fun getAdminUser() = getPropertyNonBlank("postgresUser","postgres")

    override fun getAdminPassword() = getPropertyNonBlank("postgresPassword", "password")

    override val port: String = System.getProperty(POSTGRES_PORT_PROPERTY)

    override val host = getPropertyNonBlank(POSTGRES_HOST_PROPERTY,"localhost")

    override val jdbcUrl = "jdbc:postgresql://$host:$port/${getDatabase()}"

    override val driverClass = "org.postgresql.Driver"

    override fun createSchema(connection: Connection, schemaName: String): Pair<String, String> {
        connection.use { conn ->
            conn.prepareStatement("CREATE SCHEMA IF NOT EXISTS $schemaName;").execute()
            conn.commit()
        }
        return getAdminUser() to getAdminPassword()
    }
}