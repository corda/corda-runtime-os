package net.corda.db.testkit

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.db.core.BaseDataSourceFactory
import net.corda.db.core.SQLDataSourceFactory
import net.corda.schema.configuration.DatabaseConfig
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object SQLDbUtils : AbstractDbUtils() {
    override val hostProperty: String = "sqlHost"
    override val portProperty: String = "mssqlPort"

    override val isInMemory: Boolean = System.getProperty(portProperty).isNullOrBlank()
    override val host: String = getPropertyNonBlank(hostProperty, "localhost")

    override val dbName: String = getPropertyNonBlank("sqlDb", "sql")
    override val adminUser: String = if(isInMemory) "sa" else getPropertyNonBlank("mssqlUser", "sql")
    override val adminPassword: String = if (isInMemory) "" else getPropertyNonBlank("mssqlPassword", "password")
    override var jdbcURL: String = "jdbc:sqlserver://$host:${System.getProperty(portProperty)};encrypt=true;trustServerCertificate=true;"

    override val logger: Logger = LoggerFactory.getLogger(this::class.java)
    override fun getFactory(): BaseDataSourceFactory = SQLDataSourceFactory()

    override fun createConfig(
        inMemoryDbName: String,
        dbUser: String?,
        dbPassword: String?,
        schemaName: String?
    ): Config {
        val port = System.getProperty(portProperty)
        val user = dbUser ?: adminUser
        val password = dbPassword ?: adminPassword
        if(!port.isNullOrBlank()){
            if(!schemaName.isNullOrBlank()){
                PostgresDbUtils.jdbcURL = "${PostgresDbUtils.jdbcURL}?currentSchema=$schemaName"
            }
            return ConfigFactory.empty()
                .withValue(DatabaseConfig.JDBC_URL, ConfigValueFactory.fromAnyRef(PostgresDbUtils.jdbcURL))
                .withValue(DatabaseConfig.DB_USER, ConfigValueFactory.fromAnyRef(user))
                .withValue(DatabaseConfig.DB_PASS, ConfigValueFactory.fromAnyRef(password))
        }
        else{
            return ConfigFactory.empty()
                .withValue(DatabaseConfig.JDBC_DRIVER, ConfigValueFactory.fromAnyRef("org.hsqldb.jdbc.JDBCDriver"))
                .withValue(DatabaseConfig.JDBC_URL, ConfigValueFactory.fromAnyRef("jdbc:hsqldb:mem:$inMemoryDbName"))
                .withValue(DatabaseConfig.DB_USER, ConfigValueFactory.fromAnyRef(user))
                .withValue(DatabaseConfig.DB_PASS, ConfigValueFactory.fromAnyRef(password))
        }
    }

    private fun getPropertyNonBlank(key: String, defaultValue: String): String {
        val value = System.getProperty(key)
        return if (value.isNullOrBlank()) {
            defaultValue
        } else {
            value
        }
    }
}