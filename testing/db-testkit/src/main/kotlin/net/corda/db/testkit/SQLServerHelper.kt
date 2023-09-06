package net.corda.db.testkit

class SQLServerHelper : AbstractDBHelper() {
    companion object {
        private const val MSSQL_HOST_PROPERTY = "mssqlHost"
        private const val MSSQL_PORT_PROPERTY = "mssqlPort"
    }

    override fun isInMemory() = false

    override fun getDatabase() = getPropertyNonBlank("sqlDb", "sql")

    override fun getAdminUser() = getPropertyNonBlank("mssqlUser", "sa")

    override fun getAdminPassword() = getPropertyNonBlank("mssqlPassword", "password")

    override val port: String = System.getProperty(MSSQL_PORT_PROPERTY)

    override val host = getPropertyNonBlank(MSSQL_HOST_PROPERTY, "localhost")

    override var jdbcUrl = "jdbc:sqlserver://$host:$port;encrypt=true;trustServerCertificate=true;"

    override val driverClass = "com.microsoft.sqlserver.jdbc.SQLServerDriver"

}