package net.corda.db.connection.manager.impl

import com.typesafe.config.ConfigRenderOptions
import net.corda.db.connection.manager.DBConfigurationException
import net.corda.db.core.CloseableDataSource
import net.corda.db.core.DataSourceFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.validation.ConfigurationDefaults.DB_SCHEMA_VER
import net.corda.libs.configuration.validation.getConfigurationDefaults
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.configuration.DatabaseConfig
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import javax.sql.DataSource

private object DataSourceFactoryHelper {
    val log: Logger = LoggerFactory.getLogger(DataSourceFactoryHelper::class.java)
}

/** Default values from configuration schema */
private val dbFallbackConfig = getConfigurationDefaults(ConfigKeys.DB_CONFIG, DB_SCHEMA_VER)

/**
 * Creates a [DataSource] using the [config].
 *
 * @throws DBConfigurationException If required configuration attributes are missing.
 */
fun DataSourceFactory.createFromConfig(config: SmartConfig, enablePool: Boolean = true): CloseableDataSource {
    // We are falling back to the (same) defaults from the schema for both cluster and VNode datasource configurations
    val configWithFallback = config.withFallback(dbFallbackConfig)

    DataSourceFactoryHelper.log.debug("Given configuration: ${config.toSafeConfig().root().render(ConfigRenderOptions.concise())}")
    DataSourceFactoryHelper.log.debug("Fallback configuration: ${dbFallbackConfig.root().render(ConfigRenderOptions.concise())}")

    val driver = configWithFallback.getString(DatabaseConfig.JDBC_DRIVER)
    val jdbcUrl = configWithFallback.getString(DatabaseConfig.JDBC_URL)
    val maxPoolSize = configWithFallback.getInt(DatabaseConfig.DB_POOL_MAX_SIZE)
    // The below "has path" takes care the case where DB_POOL_MIN_SIZE is null (DB_POOL_MIN_SIZE will always be present)
    val minPoolSize = if(configWithFallback.hasPath(DatabaseConfig.DB_POOL_MIN_SIZE)) {
        configWithFallback.getInt(DatabaseConfig.DB_POOL_MIN_SIZE)
    } else {
        null
    }

    val idleTimeout =
        configWithFallback.getInt(DatabaseConfig.DB_POOL_IDLE_TIMEOUT_SECONDS).toLong().run(Duration::ofSeconds)
    val maxLifetime =
        configWithFallback.getInt(DatabaseConfig.DB_POOL_MAX_LIFETIME_SECONDS).toLong().run(Duration::ofSeconds)
    val keepaliveTime =
        configWithFallback.getInt(DatabaseConfig.DB_POOL_KEEPALIVE_TIME_SECONDS).toLong().run(Duration::ofSeconds)
    val validationTimeout =
        configWithFallback.getInt(DatabaseConfig.DB_POOL_VALIDATION_TIMEOUT_SECONDS).toLong().run(Duration::ofSeconds)

    val username = if (configWithFallback.hasPath(DatabaseConfig.DB_USER)) configWithFallback.getString(DatabaseConfig.DB_USER) else
        throw DBConfigurationException(
            "No username provided to connect to cluster database. Config key ${DatabaseConfig.DB_USER} must be provided." +
                    "Provided config: ${configWithFallback.root().render()}"
        )
    val password = if (configWithFallback.hasPath(DatabaseConfig.DB_PASS)) configWithFallback.getString(DatabaseConfig.DB_PASS) else
        throw DBConfigurationException(
            "No password provided to connect to cluster database. Config key ${DatabaseConfig.DB_PASS} must be provided." +
                    "Provided config: ${configWithFallback.root().render()}"
        )

    DataSourceFactoryHelper.log.debug("Creating DB connection for: $driver, $jdbcUrl, $username, $maxPoolSize")
    return this.create(
        enablePool = enablePool,
        driverClass = driver,
        jdbcUrl = jdbcUrl,
        username = username,
        password = password,
        maximumPoolSize = maxPoolSize,
        minimumPoolSize = minPoolSize,
        idleTimeout = idleTimeout,
        maxLifetime = maxLifetime,
        keepaliveTime = keepaliveTime,
        validationTimeout = validationTimeout
    )
}