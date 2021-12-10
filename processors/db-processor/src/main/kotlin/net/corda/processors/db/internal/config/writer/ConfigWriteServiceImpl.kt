package net.corda.processors.db.internal.config.writer

import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.db.core.HikariDataSourceFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.createCoordinator
import net.corda.orm.EntityManagerFactoryFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/** An implementation of [ConfigWriteService]. */
@Suppress("Unused")
@Component(service = [ConfigWriteService::class])
class ConfigWriteServiceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigWriterSubscriptionFactory::class)
    configWriterSubscriptionFactory: ConfigWriterSubscriptionFactory,
    @Reference(service = LiquibaseSchemaMigrator::class)
    private val schemaMigrator: LiquibaseSchemaMigrator,
    @Reference(service = EntityManagerFactoryFactory::class)
    private val entityManagerFactoryFactory: EntityManagerFactoryFactory
) : ConfigWriteService {

    private val coordinator = let {
        val eventHandler = ConfigWriteEventHandler(configWriterSubscriptionFactory)
        coordinatorFactory.createCoordinator<ConfigWriteService>(eventHandler)
    }

    override fun bootstrapConfig(config: SmartConfig, instanceId: Int) {
        val dbUtils = createDbUtils(config)
        val subscribeEvent = SubscribeEvent(config, instanceId, dbUtils)
        coordinator.postEvent(subscribeEvent)
    }

    override val isRunning get() = coordinator.isRunning

    override fun start() = coordinator.start()

    override fun stop() = coordinator.stop()

    /** Creates a [DBUtils] instance for the given [config]. */
    private fun createDbUtils(config: SmartConfig): DBUtils {
        val managedEntities = setOf(ConfigEntity::class.java)
        return DBUtils(config, schemaMigrator, HikariDataSourceFactory(), entityManagerFactoryFactory, managedEntities)
    }
}