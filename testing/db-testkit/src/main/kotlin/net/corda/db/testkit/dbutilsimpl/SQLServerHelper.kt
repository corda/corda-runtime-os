package net.corda.db.testkit.dbutilsimpl

import java.sql.Connection

class SQLServerHelper : AbstractDBHelper() {
    companion object {
        private const val MSSQL_HOST_PROPERTY = "mssqlHost"
        private const val MSSQL_PORT_PROPERTY = "mssqlPort"
    }

    override fun isInMemory() = false

    override fun getDatabase() = getPropertyNonBlank("mssqlDb", "")

    override fun getAdminUser() = getPropertyNonBlank("mssqlUser", "sa")

    override fun getAdminPassword() = getPropertyNonBlank("mssqlPassword", "password")

    override val port: String = System.getProperty(MSSQL_PORT_PROPERTY)

    override val host = getPropertyNonBlank(MSSQL_HOST_PROPERTY, "localhost")

    override var jdbcUrl = "jdbc:sqlserver://$host:$port;encrypt=true;trustServerCertificate=true;${if (getDatabase().isBlank()) "" else "database=${getDatabase()};"}"

    override val driverClass = "com.microsoft.sqlserver.jdbc.SQLServerDriver"

    override fun createSchema(connection: Connection, schemaName: String): Pair<String, String> {
        val schemaUser = "user_$schemaName"
        val schemaPassword = "password${schemaName}123(!)"
        connection.use { conn ->
                conn.prepareStatement("IF NOT EXISTS ( SELECT  * FROM sys.schemas WHERE   name = N'$schemaName' )\n" +
                        "      BEGIN\n" +
                        "           EXEC('CREATE SCHEMA [$schemaName]');\n" +
                        "           CREATE LOGIN $schemaUser WITH PASSWORD = '$schemaPassword'\n" +
                        "           CREATE USER $schemaUser FOR LOGIN $schemaUser\n" +
                        "           ALTER USER $schemaUser WITH DEFAULT_SCHEMA = $schemaName\n" +
                        "           GRANT ALTER, INSERT, DELETE, SELECT, UPDATE ON SCHEMA :: $schemaName to $schemaUser\n" +
                        "           GRANT CREATE TABLE TO $schemaUser\n" +
                        "      END").execute()
                conn.commit()
        }
        return schemaUser to schemaPassword
    }

}