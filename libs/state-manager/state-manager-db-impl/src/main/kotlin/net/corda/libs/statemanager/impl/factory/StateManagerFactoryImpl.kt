package net.corda.libs.statemanager.impl.factory

import net.corda.db.core.CloseableDataSource
import net.corda.db.core.DataSourceFactoryImpl
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.getIntOrDefault
import net.corda.libs.statemanager.api.CompressionType
import net.corda.libs.statemanager.api.StateManager
import net.corda.libs.statemanager.api.StateManagerFactory
import net.corda.libs.statemanager.impl.StateManagerImpl
import net.corda.libs.statemanager.impl.compression.CompressionService
import net.corda.libs.statemanager.impl.metrics.MetricsRecorderImpl
import net.corda.libs.statemanager.impl.repository.impl.PostgresQueryProvider
import net.corda.libs.statemanager.impl.repository.impl.QueryProvider
import net.corda.libs.statemanager.impl.repository.impl.StateRepositoryImpl
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.schema.configuration.StateManagerConfig
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

@Component(service = [StateManagerFactory::class])
class StateManagerFactoryImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = CompressionService::class)
    private val compressionService: CompressionService,
) : StateManagerFactory {
    private val dataSources = ConcurrentHashMap<String, CloseableDataSource>()

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    // TODO-[CORE-16663]: factory when multiple databases are supported by the Corda platform (only Postgres now).
    private fun queryProvider(): QueryProvider {
        return PostgresQueryProvider()
    }

    override fun create(
        config: SmartConfig,
        stateType: StateManagerConfig.StateType,
        compressionType: CompressionType
    ): StateManager {
        val dataSource = dataSources.computeIfAbsent(stateType.value) {
            logger.info("Initializing Shared State Manager DataSource")

            val stateManagerConfig = config.getConfig(stateType.value)
            val user = stateManagerConfig.getString(StateManagerConfig.Database.JDBC_USER)
            val pass = stateManagerConfig.getString(StateManagerConfig.Database.JDBC_PASS)
            val jdbcUrl = stateManagerConfig.getString(StateManagerConfig.Database.JDBC_URL)
            val jdbcDiver = stateManagerConfig.getString(StateManagerConfig.Database.JDBC_DRIVER)
            val maxPoolSize = stateManagerConfig.getInt(StateManagerConfig.Database.JDBC_POOL_MAX_SIZE)
            val minPoolSize =
                stateManagerConfig.getIntOrDefault(StateManagerConfig.Database.JDBC_POOL_MIN_SIZE, maxPoolSize)
            val idleTimeout =
                stateManagerConfig.getInt(StateManagerConfig.Database.JDBC_POOL_IDLE_TIMEOUT_SECONDS).toLong().run(
                    Duration::ofSeconds
                )
            val maxLifetime =
                stateManagerConfig.getInt(StateManagerConfig.Database.JDBC_POOL_MAX_LIFETIME_SECONDS).toLong().run(
                    Duration::ofSeconds
                )
            val keepAliveTime =
                stateManagerConfig.getInt(StateManagerConfig.Database.JDBC_POOL_KEEP_ALIVE_TIME_SECONDS).toLong().run(
                    Duration::ofSeconds
                )
            val validationTimeout =
                stateManagerConfig.getInt(StateManagerConfig.Database.JDBC_POOL_VALIDATION_TIMEOUT_SECONDS).toLong()
                    .run(Duration::ofSeconds)

            DataSourceFactoryImpl().create(
                enablePool = true,
                username = user,
                password = pass,
                jdbcUrl = jdbcUrl,
                driverClass = jdbcDiver,
                idleTimeout = idleTimeout,
                maxLifetime = maxLifetime,
                keepaliveTime = keepAliveTime,
                minimumPoolSize = minPoolSize,
                maximumPoolSize = maxPoolSize,
                validationTimeout = validationTimeout
            )
        }

        return StateManagerImpl(
            lifecycleCoordinatorFactory,
            dataSource,
            StateRepositoryImpl(queryProvider(), compressionService, compressionType),
            MetricsRecorderImpl()
        )
    }
}
