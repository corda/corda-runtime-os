package net.corda.libs.statemanager.impl.factory

import net.corda.db.core.HikariDataSourceFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.getIntOrDefault
import net.corda.libs.statemanager.api.StateManager
import net.corda.libs.statemanager.api.StateManagerFactory
import net.corda.libs.statemanager.impl.StateManagerImpl
import net.corda.libs.statemanager.impl.model.v1.StateManagerEntities
import net.corda.libs.statemanager.impl.repository.impl.PostgresQueryProvider
import net.corda.libs.statemanager.impl.repository.impl.QueryProvider
import net.corda.libs.statemanager.impl.repository.impl.StateRepositoryImpl
import net.corda.orm.DbEntityManagerConfiguration
import net.corda.orm.EntityManagerFactoryFactory
import net.corda.schema.configuration.MessagingConfig.StateManager.JDBC_DRIVER
import net.corda.schema.configuration.MessagingConfig.StateManager.JDBC_PASS
import net.corda.schema.configuration.MessagingConfig.StateManager.JDBC_PERSISTENCE_UNIT_NAME
import net.corda.schema.configuration.MessagingConfig.StateManager.JDBC_POOL_IDLE_TIMEOUT_SECONDS
import net.corda.schema.configuration.MessagingConfig.StateManager.JDBC_POOL_KEEP_ALIVE_TIME_SECONDS
import net.corda.schema.configuration.MessagingConfig.StateManager.JDBC_POOL_MAX_LIFETIME_SECONDS
import net.corda.schema.configuration.MessagingConfig.StateManager.JDBC_POOL_MAX_SIZE
import net.corda.schema.configuration.MessagingConfig.StateManager.JDBC_POOL_MIN_SIZE
import net.corda.schema.configuration.MessagingConfig.StateManager.JDBC_POOL_VALIDATION_TIMEOUT_SECONDS
import net.corda.schema.configuration.MessagingConfig.StateManager.JDBC_URL
import net.corda.schema.configuration.MessagingConfig.StateManager.JDBC_USER
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.time.Duration

@Component(service = [StateManagerFactory::class])
class StateManagerFactoryImpl @Activate constructor(
    @Reference(service = EntityManagerFactoryFactory::class)
    private val entityManagerFactoryFactory: EntityManagerFactoryFactory,
) : StateManagerFactory {

    override fun create(config: SmartConfig): StateManager {
        val user = config.getString(JDBC_USER)
        val pass = config.getString(JDBC_PASS)
        val jdbcUrl = config.getString(JDBC_URL)
        val jdbcDiver = config.getString(JDBC_DRIVER)
        val persistenceUnitName = config.getString(JDBC_PERSISTENCE_UNIT_NAME)
        val maxPoolSize = config.getInt(JDBC_POOL_MAX_SIZE)
        val minPoolSize = config.getIntOrDefault(JDBC_POOL_MIN_SIZE, maxPoolSize)
        val idleTimeout = config.getInt(JDBC_POOL_IDLE_TIMEOUT_SECONDS).toLong().run(Duration::ofSeconds)
        val maxLifetime = config.getInt(JDBC_POOL_MAX_LIFETIME_SECONDS).toLong().run(Duration::ofSeconds)
        val keepAliveTime = config.getInt(JDBC_POOL_KEEP_ALIVE_TIME_SECONDS).toLong().run(Duration::ofSeconds)
        val validationTimeout = config.getInt(JDBC_POOL_VALIDATION_TIMEOUT_SECONDS).toLong().run(Duration::ofSeconds)

        val dataSource = HikariDataSourceFactory().create(
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

        val entityManagerFactory = entityManagerFactoryFactory.create(
            persistenceUnitName,
            StateManagerEntities.classes,
            DbEntityManagerConfiguration(dataSource)
        )

        return StateManagerImpl(
            StateRepositoryImpl(queryProvider()),
            entityManagerFactory
        )
    }

    // TODO-[CORE-16663]: factory when multiple databases are supported at a platform level (only Postgres supported now).
    private fun queryProvider(): QueryProvider {
        return PostgresQueryProvider()
    }
}
