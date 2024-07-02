package net.corda.processors.persistence.internal

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.cpk.read.CpkReadService
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.entityprocessor.FlowPersistenceService
import net.corda.ledger.persistence.LedgerPersistenceService
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
import net.corda.processors.persistence.PersistenceProcessor
import net.corda.schema.configuration.BootConfig.BOOT_DB
import net.corda.utilities.debug
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory


/**
 *  All this processor classes actually does is set up the database connection manager once Corda configuration
 *  is available.
 *
 *  The entry point to the persistence processor is actually [LedgerPersistenceRequestProcessor]
 */

@Suppress("Unused", "LongParameterList")
@Component(service = [PersistenceProcessor::class])
class PersistenceProcessorImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = DbConnectionManager::class)
    private val dbConnectionManager: DbConnectionManager,
    @Reference(service = JpaEntitiesRegistry::class)
    private val entitiesRegistry: JpaEntitiesRegistry,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = CpiInfoReadService::class)
    private val cpiInfoReadService: CpiInfoReadService,
    @Reference(service = CpkReadService::class)
    private val cpkReadService: CpkReadService,
    @Reference(service = LedgerPersistenceService::class)
    private val ledgerPersistenceService: LedgerPersistenceService,
    @Reference(service = FlowPersistenceService::class)
    private val flowPersistenceService: FlowPersistenceService,
) : PersistenceProcessor {
    init {
        entitiesRegistry.register(CordaDb.CordaCluster.persistenceUnitName, ConfigurationEntities.classes)
    }

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val dependentComponents = DependentComponents.of(
        ::dbConnectionManager,
        ::configurationReadService,
        ::cpiInfoReadService,
        ::cpkReadService,
        ::ledgerPersistenceService,
        ::flowPersistenceService,
    )
    private val lifecycleCoordinator = coordinatorFactory.createCoordinator<PersistenceProcessorImpl>(dependentComponents, ::eventHandler)

    override fun start(bootConfig: SmartConfig) {
        log.info("Persistence processor starting.")
        lifecycleCoordinator.start()
        lifecycleCoordinator.postEvent(BootConfigEvent(bootConfig))
    }

    override fun stop() {
        log.info("Persistence processor stopping.")
        lifecycleCoordinator.stop()
    }

    @Suppress("ComplexMethod", "NestedBlockDepth")
    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        log.debug { "Persistence processor received event $event." }

        when (event) {
            is StartEvent -> {
                // Nothing to do
            }
            is StopEvent -> {
                // Nothing to do
            }
            is RegistrationStatusChangeEvent -> onRegistrationStatusChangeEvent(event, coordinator)
            is ConfigChangedEvent  -> {
                // Nothing to do
            }
            is BootConfigEvent -> onBootConfigEvent(event)
            else -> log.error("Unexpected event $event!")
        }
    }

    private fun onBootConfigEvent(event: BootConfigEvent) {
        val bootstrapConfig = event.config

        log.info("Bootstrapping {}", configurationReadService::class.simpleName)
        configurationReadService.bootstrapConfig(bootstrapConfig)

        log.info("Bootstrapping {}", dbConnectionManager::class.simpleName)
        dbConnectionManager.bootstrap(bootstrapConfig.getConfig(BOOT_DB))
    }

    private fun onRegistrationStatusChangeEvent(
        event: RegistrationStatusChangeEvent,
        coordinator: LifecycleCoordinator
    ) {
        log.info("Persistence processor is ${event.status}")
        coordinator.updateStatus(event.status)
    }

    data class BootConfigEvent(val config: SmartConfig) : LifecycleEvent
}
