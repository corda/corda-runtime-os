package net.corda.processors.uniqueness.internal

import net.corda.configuration.read.ConfigurationReadService
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.datamodel.ConfigurationEntities
import net.corda.lifecycle.DependentComponents
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.orm.JpaEntitiesRegistry
import net.corda.processors.uniqueness.UniquenessProcessor
import net.corda.schema.configuration.BootConfig.BOOT_DB
import net.corda.uniqueness.checker.UniquenessCheckerLifecycle
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory

/**
 * Uniqueness processor implementation.
 */
@Suppress("LongParameterList")
@Component(service = [UniquenessProcessor::class])
class UniquenessProcessorImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = DbConnectionManager::class)
    private val dbConnectionManager: DbConnectionManager,
    @Reference(service = JpaEntitiesRegistry::class)
    private val jpaEntitiesRegistry: JpaEntitiesRegistry,
    @Reference(service = UniquenessCheckerLifecycle::class)
    private val uniquenessChecker: UniquenessCheckerLifecycle
) : UniquenessProcessor {

    init {
        jpaEntitiesRegistry.register(CordaDb.CordaCluster.persistenceUnitName, ConfigurationEntities.classes)
    }

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val dependentComponents = DependentComponents.of(
        ::configurationReadService,
        ::dbConnectionManager,
        ::uniquenessChecker
    )

    private val lifecycleCoordinator =
        coordinatorFactory.createCoordinator<UniquenessProcessorImpl>(dependentComponents, ::eventHandler)

    override fun start(bootConfig: SmartConfig) {
        log.info("Uniqueness processor starting.")
        lifecycleCoordinator.start()
        lifecycleCoordinator.postEvent(BootConfigEvent(bootConfig))
    }

    override fun stop() {
        log.info("Uniqueness processor stopping.")
        lifecycleCoordinator.stop()
    }

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        log.info("Uniqueness processor received event $event.")
        when (event) {
            is StartEvent -> {
                // Nothing to do
            }
            is StopEvent -> {
                // Nothing to do
            }
            is BootConfigEvent -> {
                val bootstrapConfig = event.config

                log.trace("Bootstrapping {}", configurationReadService::class.simpleName)
                configurationReadService.bootstrapConfig(bootstrapConfig)

                log.trace("Bootstrapping {}", dbConnectionManager::class.simpleName)
                dbConnectionManager.bootstrap(bootstrapConfig.getConfig(BOOT_DB))
            }
            is RegistrationStatusChangeEvent -> {
                log.info("Uniqueness processor is ${event.status}")
                coordinator.updateStatus(event.status)
            }
            else -> {
                log.warn("Unexpected event $event!")
            }
        }
    }

    data class BootConfigEvent(val config: SmartConfig) : LifecycleEvent
}
