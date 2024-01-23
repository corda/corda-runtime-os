package net.corda.applications.workers.workercommon

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.mergeOver
import net.corda.schema.configuration.BootConfig
import net.corda.schema.configuration.StateManagerConfig

object StateManagerConfigHelper {

    private val stateTypes = setOf(
        StateManagerConfig.StateType.FLOW_CHECKPOINT,
        StateManagerConfig.StateType.P2P_SESSION,
        StateManagerConfig.StateType.FLOW_MAPPING,
        StateManagerConfig.StateType.KEY_ROTATION,
        StateManagerConfig.StateType.TOKEN_POOL_CACHE,
    )

    private const val DEFAULT_DATABASE_TYPE = "DATABASE"
    private const val DEFAULT_POSTGRES_DRIVER = "org.postgresql.Driver"
    private const val DEFAULT_JDBC_POOL_MIN_SIZE = 0
    private const val DEFAULT_JDBC_POOL_MAX_SIZE = 5
    private const val DEFAULT_JDBC_POOL_IDLE_TIMEOUT_SECONDS = 120
    private const val DEFAULT_JDBC_POOL_MAX_LIFETIME_SECONDS = 1800
    private const val DEFAULT_JDBC_POOL_KEEP_ALIVE_TIME_SECONDS = 0
    private const val DEFAULT_JDBC_POOL_VALIDATION_TIMEOUT_SECONDS = 5

    fun createStateManagerConfigFromClusterDb(config: Config): Config {
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
                StateManagerConfig.TYPE to DEFAULT_DATABASE_TYPE,
                StateManagerConfig.Database.JDBC_DRIVER to ConfigValueFactory.fromAnyRef(
                    DEFAULT_POSTGRES_DRIVER
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
        return stateTypes.map { type ->
            ConfigFactory.empty().withValue(
                "${BootConfig.BOOT_STATE_MANAGER}.$type",
                stateManagerTypeConfig.root()
            )
        }.mergeOver(ConfigFactory.empty())
    }
}