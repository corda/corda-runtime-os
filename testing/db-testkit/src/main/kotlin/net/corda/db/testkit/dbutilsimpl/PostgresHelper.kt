package net.corda.db.testkit.dbutilsimpl

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

    override var jdbcUrl = "jdbc:postgresql://$host:$port/${getDatabase()}"

    override val driverClass = "org.postgresql.Driver"

}