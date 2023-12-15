package net.corda.libs.statemanager.impl.factory

import net.corda.db.core.CloseableDataSource
import net.corda.db.core.HikariDataSourceFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.getIntOrDefault
import net.corda.libs.statemanager.api.StateManager
import net.corda.libs.statemanager.api.StateManagerFactory
import net.corda.libs.statemanager.impl.StateManagerImpl
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
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Component(service = [StateManagerFactory::class])
class StateManagerFactoryImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
) : StateManagerFactory {
    private val lock = ReentrantLock()
    private var dataSource: CloseableDataSource? = null

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    // TODO-[CORE-16663]: factory when multiple databases are supported by the Corda platform (only Postgres now).
    private fun queryProvider(): QueryProvider {
        return PostgresQueryProvider()
    }

    override fun create(config: SmartConfig): StateManager {
        lock.withLock {
            if (dataSource == null) {
                logger.info("Initializing Shared State Manager DataSource")

                val user = config.getString(StateManagerConfig.Database.JDBC_USER)
                val pass = config.getString(StateManagerConfig.Database.JDBC_PASS)
                val jdbcUrl = config.getString(StateManagerConfig.Database.JDBC_URL)
                val jdbcDiver = config.getString(StateManagerConfig.Database.JDBC_DRIVER)
                val maxPoolSize = config.getInt(StateManagerConfig.Database.JDBC_POOL_MAX_SIZE)
                val minPoolSize = config.getIntOrDefault(StateManagerConfig.Database.JDBC_POOL_MIN_SIZE, maxPoolSize)
                val idleTimeout = config.getInt(StateManagerConfig.Database.JDBC_POOL_IDLE_TIMEOUT_SECONDS).toLong().run(
                    Duration::ofSeconds
                )
                val maxLifetime = config.getInt(StateManagerConfig.Database.JDBC_POOL_MAX_LIFETIME_SECONDS).toLong().run(
                    Duration::ofSeconds
                )
                val keepAliveTime = config.getInt(StateManagerConfig.Database.JDBC_POOL_KEEP_ALIVE_TIME_SECONDS).toLong().run(
                    Duration::ofSeconds
                )
                val validationTimeout =
                    config.getInt(StateManagerConfig.Database.JDBC_POOL_VALIDATION_TIMEOUT_SECONDS).toLong().run(Duration::ofSeconds)

                dataSource = HikariDataSourceFactory().create(
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
        }

        return StateManagerImpl(
            lifecycleCoordinatorFactory,
            dataSource!!,
            StateRepositoryImpl(queryProvider())
        )
    }
}
