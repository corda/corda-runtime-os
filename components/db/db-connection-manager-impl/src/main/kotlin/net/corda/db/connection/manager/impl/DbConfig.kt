package net.corda.db.connection.manager.impl

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.db.core.DataSourceFactory
import net.corda.libs.configuration.SmartConfig
import javax.sql.DataSource

// TODO - move conf paths to api repo and remove/replace the copies in DbProcessorImpl
internal const val CONFIG_DB_DRIVER = "database.cluster.driver"
internal const val CONFIG_JDBC_URL = "database.cluster.jdbc.url"
internal const val CONFIG_DB_USER = "database.cluster.user"
internal const val CONFIG_DB_PASS = "database.cluster.pass"
internal const val CONFIG_DB_MAX_POOL_SIZE = "database.max.pool.size"

internal const val CONFIG_DB_DRIVER_DEFAULT = "org.postgresql.Driver"
internal const val CONFIG_DB_JDBC_URL_DEFAULT = "jdbc:postgresql://cluster-db:5432/cordacluster"
internal const val CONFIG_DB_MAX_POOL_SIZE_DEFAULT = 10

/**
 * Creates a [DataSource] using the [config].
 *
 * @throws DBProcessorException If required configuration attributes are missing.
 */
fun DataSourceFactory.createFromConfig(config: SmartConfig): DataSource {
    val fallbackConfig = ConfigFactory.empty()
        .withValue(CONFIG_DB_DRIVER, ConfigValueFactory.fromAnyRef(CONFIG_DB_DRIVER_DEFAULT))
        .withValue(CONFIG_JDBC_URL, ConfigValueFactory.fromAnyRef(CONFIG_DB_JDBC_URL_DEFAULT))
        .withValue(CONFIG_DB_MAX_POOL_SIZE, ConfigValueFactory.fromAnyRef(CONFIG_DB_MAX_POOL_SIZE_DEFAULT))
    val configWithFallback = config.withFallback(fallbackConfig)

    val driver = configWithFallback.getString(CONFIG_DB_DRIVER)
    val jdbcUrl = configWithFallback.getString(CONFIG_JDBC_URL)
    val maxPoolSize = configWithFallback.getInt(CONFIG_DB_MAX_POOL_SIZE)

    val username = if (config.hasPath(CONFIG_DB_USER)) config.getString(CONFIG_DB_USER) else
        throw DBConfigurationException(
            "No username provided to connect to cluster database. Config key $CONFIG_DB_USER must be provided." +
                    "Provided config: ${config.root().render()}"
        )
    val password = if (config.hasPath(CONFIG_DB_PASS)) config.getString(CONFIG_DB_PASS) else
        throw DBConfigurationException(
            "No password provided to connect to cluster database. Config key $CONFIG_DB_PASS must be provided." +
                    "Provided config: ${config.root().render()}"
        )

    return this.create(driver, jdbcUrl, username, password, false, maxPoolSize)
}