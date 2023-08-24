package net.corda.db.testkit

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.db.core.BaseDataSourceFactory
import net.corda.db.core.PostgresDataSourceFactory
import net.corda.schema.configuration.DatabaseConfig
import org.slf4j.Logger
import org.slf4j.LoggerFactory


object PostgresDbUtils : DbUtilsAbstract() {
    override val host_property: String = "postgresHost"
    override val port_property: String = "postgresPort"

    override val isInMemory: Boolean = System.getProperty(port_property).isNullOrBlank()
    override val host: String = getPropertyNonBlank(host_property, "localhost")

    override val db_name: String = getPropertyNonBlank("postgresDb","postgres")
    override val admin_user: String = if(isInMemory) "sa" else getPropertyNonBlank("postgresUser","postgres")
    override val admin_password: String = if (isInMemory) "" else getPropertyNonBlank("postgresPassword","password")

    override var jdbcURL: String = "jdbc:postgresql://${host}:${System.getProperty(port_property)}/${db_name}"

    override val logger: Logger = LoggerFactory.getLogger(this::class.java)

    override fun getFactory(): BaseDataSourceFactory = PostgresDataSourceFactory()


    override fun createConfig(
        inMemoryDbName: String,
        dbUser: String?,
        dbPassword: String?,
        schemaName: String?
    ): Config {
        val port = System.getProperty(port_property)
        val user = dbUser ?: admin_user
        val password = dbPassword ?: admin_password
        if(!port.isNullOrBlank()){
            if(!schemaName.isNullOrBlank()){
                jdbcURL = "$jdbcURL?currentSchema=$schemaName"
            }
            return ConfigFactory.empty()
                .withValue(DatabaseConfig.JDBC_URL, ConfigValueFactory.fromAnyRef(jdbcURL))
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



