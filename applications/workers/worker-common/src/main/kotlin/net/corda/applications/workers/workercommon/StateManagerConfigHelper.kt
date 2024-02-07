package net.corda.applications.workers.workercommon

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.mergeOver
import net.corda.schema.configuration.BootConfig
import net.corda.schema.configuration.StateManagerConfig

object StateManagerConfigHelper {

    private const val DEFAULT_TYPE = "Database"
    internal const val DEFAULT_DRIVER = "org.postgresql.Driver"
    internal const val DEFAULT_JDBC_POOL_MIN_SIZE = 0
    internal const val DEFAULT_JDBC_POOL_MAX_SIZE = 5
    internal const val DEFAULT_JDBC_POOL_IDLE_TIMEOUT_SECONDS = 120
    internal const val DEFAULT_JDBC_POOL_MAX_LIFETIME_SECONDS = 1800
    internal const val DEFAULT_JDBC_POOL_KEEP_ALIVE_TIME_SECONDS = 0
    internal const val DEFAULT_JDBC_POOL_VALIDATION_TIMEOUT_SECONDS = 5

    fun createStateManagerConfigFromClusterDb(config: Config): Config {
        if (!config.hasPath(BootConfig.BOOT_JDBC_URL)) throw IllegalArgumentException("${BootConfig.BOOT_JDBC_URL} not provided")
        val clusterStateManagerConfig = createClusterConfig(config).withFallback(getDatabaseDefaults())
        return duplicateConfigForAllStateTypes(clusterStateManagerConfig)
    }

    fun createStateManagerConfigFromCli(stateManagerCliArgs: Map<String, String>): Config {
        if (stateManagerCliArgs.isEmpty()) return ConfigFactory.empty()

        val databaseDefaults = getDatabaseDefaults()

        return duplicateConfigForAllStateTypes(
            ConfigFactory.parseMap(stateManagerCliArgs).withFallback(databaseDefaults)
        )
    }

    private fun getDatabaseDefaults() =
        ConfigFactory.parseMap(
            mapOf(
                StateManagerConfig.TYPE to DEFAULT_TYPE,
                StateManagerConfig.Database.JDBC_DRIVER to ConfigValueFactory.fromAnyRef(
                    DEFAULT_DRIVER
                ),
                StateManagerConfig.Database.JDBC_POOL_MIN_SIZE to ConfigValueFactory.fromAnyRef(
                    DEFAULT_JDBC_POOL_MIN_SIZE
                ),
                StateManagerConfig.Database.JDBC_POOL_MAX_SIZE to ConfigValueFactory.fromAnyRef(
                    DEFAULT_JDBC_POOL_MAX_SIZE
                ),
                StateManagerConfig.Database.JDBC_POOL_IDLE_TIMEOUT_SECONDS to ConfigValueFactory.fromAnyRef(
                    DEFAULT_JDBC_POOL_IDLE_TIMEOUT_SECONDS
                ),
                StateManagerConfig.Database.JDBC_POOL_MAX_LIFETIME_SECONDS to ConfigValueFactory.fromAnyRef(
                    DEFAULT_JDBC_POOL_MAX_LIFETIME_SECONDS
                ),
                StateManagerConfig.Database.JDBC_POOL_KEEP_ALIVE_TIME_SECONDS to ConfigValueFactory.fromAnyRef(
                    DEFAULT_JDBC_POOL_KEEP_ALIVE_TIME_SECONDS
                ),
                StateManagerConfig.Database.JDBC_POOL_VALIDATION_TIMEOUT_SECONDS to ConfigValueFactory.fromAnyRef(
                    DEFAULT_JDBC_POOL_VALIDATION_TIMEOUT_SECONDS
                )
            )
        )

    private fun createClusterConfig(config: Config): Config {
        return ConfigFactory.parseMap(
            mapOf(
                StateManagerConfig.Database.JDBC_URL to config.getString(BootConfig.BOOT_JDBC_URL),
                StateManagerConfig.Database.JDBC_USER to config.getString(BootConfig.BOOT_JDBC_USER),
                StateManagerConfig.Database.JDBC_PASS to config.getString(BootConfig.BOOT_JDBC_PASS),
            )
        )
    }

    private fun duplicateConfigForAllStateTypes(stateManagerTypeConfig: Config): Config {
        return StateManagerConfig.StateType.values().map { type ->
            ConfigFactory.empty().withValue(
                "${BootConfig.BOOT_STATE_MANAGER}.${type.value}",
                stateManagerTypeConfig.root()
            )
        }.mergeOver(ConfigFactory.empty())
    }
}