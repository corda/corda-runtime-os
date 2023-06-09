package net.corda.db.connection.manager

import com.typesafe.config.ConfigRenderOptions
import com.typesafe.config.ConfigValueFactory
import net.corda.db.core.CloseableDataSource
import net.corda.db.core.DataSourceFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.validation.ConfigurationDefaults.DB_SCHEMA_VER
import net.corda.libs.configuration.validation.getConfigurationDefaults
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.configuration.DatabaseConfig
import org.slf4j.LoggerFactory
import java.time.Duration
import javax.sql.DataSource

internal object DbConfig {
    val log = LoggerFactory.getLogger(DbConfig::class.java)
}

/** Default values from configuration schema */
val dbFallbackConfig = getConfigurationDefaults(ConfigKeys.DB_CONFIG, DB_SCHEMA_VER)

/**
 * Creates a [DataSource] using the [config].
 *
 * @throws DBConfigurationException If required configuration attributes are missing.
 */
fun DataSourceFactory.createFromConfig(config: SmartConfig): CloseableDataSource {
    val configWithFallback = config.withFallback(dbFallbackConfig)

    DbConfig.log.debug("Given configuration: ${config.toSafeConfig().root().render(ConfigRenderOptions.concise())}")
    DbConfig.log.debug("Fallback configuration: ${dbFallbackConfig.root().render(ConfigRenderOptions.concise())}")

    // TODO all the following configuration reading should probably be changed to mandatory (i.e. throw if not found),
    //  since, with the config defaulting process it should all be there.
    val driver = configWithFallback.getString(DatabaseConfig.JDBC_DRIVER)
    val jdbcUrl = configWithFallback.getString(DatabaseConfig.JDBC_URL)
    val maxPoolSize = configWithFallback.getInt(DatabaseConfig.DB_POOL_MAX_SIZE)
    val minPoolSize = if(configWithFallback.hasPath(DatabaseConfig.DB_POOL_MIN_SIZE)) {
        configWithFallback.getInt(DatabaseConfig.DB_POOL_MIN_SIZE)
    } else {
        null
    }

    val idleTimeout = if (configWithFallback.hasPath(DatabaseConfig.DB_POOL_IDLE_TIMEOUT)) {
        configWithFallback.getInt(DatabaseConfig.DB_POOL_IDLE_TIMEOUT)
    } else {
        120
    }.toLong().run(Duration::ofSeconds)

    val maxLifetime = if (configWithFallback.hasPath(DatabaseConfig.DB_POOL_MAX_LIFETIME)) {
        configWithFallback.getInt(DatabaseConfig.DB_POOL_MAX_LIFETIME)
    } else {
        1800
    }.toLong().run(Duration::ofSeconds)

    val keepaliveTime = if (configWithFallback.hasPath(DatabaseConfig.DB_POOL_KEEP_ALIVE_TIME)) {
        configWithFallback.getInt(DatabaseConfig.DB_POOL_KEEP_ALIVE_TIME)
    } else {
        0
    }.toLong().run(Duration::ofSeconds)

    val validationTimeout = if (configWithFallback.hasPath(DatabaseConfig.DB_POOL_VALIDATION_TIMEOUT)) {
        configWithFallback.getInt(DatabaseConfig.DB_POOL_VALIDATION_TIMEOUT)
    } else {
        5
    }.toLong().run(Duration::ofSeconds)

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

    DbConfig.log.debug("Creating DB connection for: $driver, $jdbcUrl, $username, $maxPoolSize")
    return this.create(
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

@Suppress("LongParameterList")
fun createDbConfig(
    smartConfigFactory: SmartConfigFactory,
    username: String,
    password: String,
    jdbcDriver: String? = null,
    jdbcUrl: String? = null,
    maxPoolSize: Int? = null,
    minPoolSize: Int? = null,
    key: String = "database-password"
): SmartConfig {
    var config =
        smartConfigFactory.makeSecret(password, key).atPath(DatabaseConfig.DB_PASS)
            .withValue(DatabaseConfig.DB_USER, ConfigValueFactory.fromAnyRef(username))
    if(null != jdbcDriver)
        config = config.withValue(DatabaseConfig.JDBC_DRIVER, ConfigValueFactory.fromAnyRef(jdbcDriver))
    if(null != jdbcUrl)
        config = config.withValue(DatabaseConfig.JDBC_URL, ConfigValueFactory.fromAnyRef(jdbcUrl))
    if(null != maxPoolSize)
        config = config.withValue(DatabaseConfig.DB_POOL_MAX_SIZE, ConfigValueFactory.fromAnyRef(maxPoolSize))
    if(null != minPoolSize)
        config = config.withValue(DatabaseConfig.DB_POOL_MIN_SIZE, ConfigValueFactory.fromAnyRef(minPoolSize))
    return config

}