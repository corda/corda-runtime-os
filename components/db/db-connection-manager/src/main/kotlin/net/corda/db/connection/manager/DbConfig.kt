package net.corda.db.connection.manager

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import com.typesafe.config.ConfigValueFactory
import net.corda.db.core.DataSourceFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.schema.configuration.ConfigDefaults
import net.corda.schema.configuration.ConfigKeys
import org.slf4j.LoggerFactory
import javax.sql.DataSource

internal const val DEFAULT_JDBC_URL = "jdbc:postgresql://cluster-db:5432/cordacluster"

internal object DbConfigLogger {
    val log = LoggerFactory.getLogger(DbConfigLogger::class.java)
}

/**
 * TODO - this should go in corda-api
 */
val dbFallbackConfig: Config = ConfigFactory.empty()
    .withValue(ConfigKeys.JDBC_DRIVER, ConfigValueFactory.fromAnyRef(ConfigDefaults.JDBC_DRIVER))
    .withValue(ConfigKeys.JDBC_URL, ConfigValueFactory.fromAnyRef(DEFAULT_JDBC_URL))
    .withValue(ConfigKeys.DB_POOL_MAX_SIZE, ConfigValueFactory.fromAnyRef(ConfigDefaults.DB_POOL_MAX_SIZE))

/**
 * Creates a [DataSource] using the [config].
 *
 * @throws DBConfigurationException If required configuration attributes are missing.
 */
fun DataSourceFactory.createFromConfig(config: SmartConfig): DataSource {
    val configWithFallback = config.withFallback(dbFallbackConfig)

    DbConfigLogger.log.debug("Given configuration: ${config.toSafeConfig().root().render(ConfigRenderOptions.concise())}")
    DbConfigLogger.log.debug("Fallback configuration: ${dbFallbackConfig.root().render(ConfigRenderOptions.concise())}")

    val driver = configWithFallback.getString(ConfigKeys.JDBC_DRIVER)
    val jdbcUrl = configWithFallback.getString(ConfigKeys.JDBC_URL)
    val maxPoolSize = configWithFallback.getInt(ConfigKeys.DB_POOL_MAX_SIZE)

    val username = if (configWithFallback.hasPath(ConfigKeys.DB_USER)) configWithFallback.getString(ConfigKeys.DB_USER) else
        throw DBConfigurationException(
            "No username provided to connect to cluster database. Config key ${ConfigKeys.DB_USER} must be provided." +
                    "Provided config: ${configWithFallback.root().render()}"
        )
    val password = if (configWithFallback.hasPath(ConfigKeys.DB_PASS)) configWithFallback.getString(ConfigKeys.DB_PASS) else
        throw DBConfigurationException(
            "No password provided to connect to cluster database. Config key ${ConfigKeys.DB_PASS} must be provided." +
                    "Provided config: ${configWithFallback.root().render()}"
        )

    DbConfigLogger.log.debug("Creating DB connection for: $driver, $jdbcUrl, $username, $maxPoolSize")
    return this.create(driver, jdbcUrl, username, password, false, maxPoolSize)
}

@Suppress("LongParameterList")
fun createDbConfig(
    smartConfigFactory: SmartConfigFactory,
    username: String,
    password: String,
    jdbcDriver: String? = null,
    jdbcUrl: String? = null,
    maxPoolSize: Int? = null,
): SmartConfig {
    var config =
        smartConfigFactory.makeSecret(password).atPath(ConfigKeys.DB_PASS)
            .withValue(ConfigKeys.DB_USER, ConfigValueFactory.fromAnyRef(username))
    if(null != jdbcDriver)
        config = config.withValue(ConfigKeys.JDBC_DRIVER, ConfigValueFactory.fromAnyRef(jdbcDriver))
    if(null != jdbcUrl)
        config = config.withValue(ConfigKeys.JDBC_URL, ConfigValueFactory.fromAnyRef(jdbcUrl))
    if(null != maxPoolSize)
        config = config.withValue(ConfigKeys.DB_POOL_MAX_SIZE, ConfigValueFactory.fromAnyRef(maxPoolSize))
    return config

}