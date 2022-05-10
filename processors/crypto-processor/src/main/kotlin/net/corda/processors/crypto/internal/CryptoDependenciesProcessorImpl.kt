package net.corda.processors.crypto.internal

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
import net.corda.processors.crypto.CryptoDependenciesProcessor
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Suppress("LongParameterList")
@Component(service = [CryptoDependenciesProcessor::class])
class CryptoDependenciesProcessorImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = DbConnectionManager::class)
    private val dbConnectionManager: DbConnectionManager,
    @Reference(service = JpaEntitiesRegistry::class)
    private val entitiesRegistry: JpaEntitiesRegistry
) : CryptoDependenciesProcessor {
    private companion object {
        val log = contextLogger()
    }

    init {
        // define the different DB Entity Sets
        //  entities can be in different packages, but all JPA classes must be passed in.
        entitiesRegistry.register(CordaDb.CordaCluster.persistenceUnitName, ConfigurationEntities.classes)
    }

    private val lifecycleCoordinator = coordinatorFactory.createCoordinator<CryptoDependenciesProcessor>(::eventHandler)

    private val dependentComponents = DependentComponents.of(
        ::configurationReadService,
        ::dbConnectionManager
    )

    override val isRunning: Boolean
        get() = lifecycleCoordinator.isRunning

    override fun start(bootConfig: SmartConfig) {
        log.info("Crypto processor starting.")
        lifecycleCoordinator.start()
        lifecycleCoordinator.postEvent(BootConfigEvent(bootConfig))
    }

    override fun stop() {
        log.info("Crypto processor stopping.")
        lifecycleCoordinator.stop()
    }

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        log.debug { "Crypto processor received event $event." }
        when (event) {
            is StartEvent -> {
                dependentComponents.registerAndStartAll(coordinator)
            }
            is StopEvent -> {
                dependentComponents.stopAll()
            }
            is RegistrationStatusChangeEvent -> {
                log.info("Crypto processor is ${event.status}")
                coordinator.updateStatus(event.status)
            }
            is BootConfigEvent -> {
                log.info("Bootstrapping {}", configurationReadService::class.simpleName)
                configurationReadService.bootstrapConfig(event.config)

                log.info("Bootstrapping {}", dbConnectionManager::class.simpleName)
                dbConnectionManager.bootstrap(event.config.getConfig(ConfigKeys.DB_CONFIG))
            }
            else -> {
                log.warn("Unexpected event $event!")
            }
        }
    }
}

