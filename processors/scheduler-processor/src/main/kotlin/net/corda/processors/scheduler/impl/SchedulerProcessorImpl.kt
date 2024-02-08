package net.corda.processors.scheduler.impl

import net.corda.components.scheduler.Schedule
import net.corda.components.scheduler.TriggerPublisher
import net.corda.components.scheduler.impl.SchedulerFactoryImpl
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.scheduler.datamodel.SchedulerEntities
import net.corda.lifecycle.DependentComponents
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.orm.JpaEntitiesRegistry
import net.corda.processors.scheduler.SchedulerProcessor
import net.corda.schema.Schemas.ScheduledTask
import net.corda.schema.configuration.BootConfig
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory

@Component(service = [SchedulerProcessor::class])
class SchedulerProcessorImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = DbConnectionManager::class)
    private val dbConnectionManager: DbConnectionManager,
    @Reference(service = JpaEntitiesRegistry::class)
    private val entitiesRegistry: JpaEntitiesRegistry,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = TriggerPublisher::class)
    private val triggerPublisher: TriggerPublisher,
    ): SchedulerProcessor {
    init {
        // define the different DB Entity Sets
        //  entities can be in different packages, but all JPA classes must be passed in.
        entitiesRegistry.register(
            CordaDb.CordaCluster.persistenceUnitName,
            SchedulerEntities.classes
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val dependentComponents = DependentComponents.of(
        ::dbConnectionManager,
        ::triggerPublisher
        // TODO - CORE-16331: plug in config
        //::configurationReadService,
    )
    private val lifecycleCoordinator =
        coordinatorFactory.createCoordinator<SchedulerProcessorImpl>(dependentComponents, ::eventHandler)

    // now just hardcoding schedulers here until CORE-16331 is picked up, when we should take this from config
    private val schedules = listOf(
        Schedule(ScheduledTask.SCHEDULED_TASK_NAME_DB_PROCESSOR,
            600, ScheduledTask.SCHEDULED_TASK_TOPIC_DB_PROCESSOR
        ),
        Schedule(
            ScheduledTask.SCHEDULED_TASK_NAME_SESSION_TIMEOUT,
            60, ScheduledTask.SCHEDULED_TASK_TOPIC_FLOW_PROCESSOR
        ),
        Schedule(
            ScheduledTask.SCHEDULED_TASK_NAME_FLOW_CHECKPOINT_TERMINATION,
            60, ScheduledTask.SCHEDULED_TASK_TOPIC_FLOW_PROCESSOR
        ),
        Schedule(
            ScheduledTask.SCHEDULED_TASK_NAME_MAPPER_CLEANUP,
            60, ScheduledTask.SCHEDULED_TASK_TOPIC_MAPPER_PROCESSOR
        ),
        Schedule(
            ScheduledTask.SCHEDULE_TASK_NAME_FLOW_STATUS_CLEANUP,
            60, ScheduledTask.SCHEDULED_TASK_TOPIC_FLOW_STATUS_PROCESSOR
        )
    )
    private var schedulers: Schedulers? = null

    override fun start(bootConfig: SmartConfig) {
        logger.info("Scheduler processor starting.")
        lifecycleCoordinator.start()
        lifecycleCoordinator.postEvent(BootConfigEvent(bootConfig))
    }

    override fun stop() {
        logger.info("Scheduler processor stopping.")
        lifecycleCoordinator.stop()
    }

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> logger.debug("Scheduler Processor Start")
            is RegistrationStatusChangeEvent -> onRegistrationStatusChangeEvent(event, coordinator)
            is ConfigChangedEvent -> onConfigChangedEvent(event)
            is BootConfigEvent -> onBootConfigEvent(event)
            is StopEvent -> onStopEvent()
            else -> logger.error("Unexpected event $event!")
        }
    }

    private fun onBootConfigEvent(event: BootConfigEvent) {
        val bootstrapConfig = event.config

        logger.info("Bootstrapping DB connection Manager")
        dbConnectionManager.bootstrap(bootstrapConfig.getConfig(BootConfig.BOOT_DB))

        logger.info("Bootstrapping config read service")
        configurationReadService.bootstrapConfig(bootstrapConfig)
    }

    private fun onRegistrationStatusChangeEvent(
        event: RegistrationStatusChangeEvent,
        coordinator: LifecycleCoordinator
    ) {
        logger.info("Scheduler processor is ${event.status}")
        if (event.status == LifecycleStatus.UP) {
            // TODO - CORE-16331: plug in config
//            coordinator.createManagedResource(CONFIG) {
//                configurationReadService.registerComponentForUpdates(
//                    coordinator, setOf(
//                        ConfigKeys.SCHEDULER_CONFIG
//                    )
//                )
//            }
            if (null == schedulers) {
                val schedulerFactory = SchedulerFactoryImpl(
                    triggerPublisher,
                    dbConnectionManager,
                    coordinatorFactory)
                schedulers = Schedulers(schedules, schedulerFactory)
                schedulers?.start()
            }

        }
        coordinator.updateStatus(event.status)
    }

    private fun onConfigChangedEvent(
        event: ConfigChangedEvent,
    ) {
        schedulers?.onConfigChanged(event)
    }

    private fun onStopEvent() {
        schedulers?.stop()
        schedulers = null
    }

    data class BootConfigEvent(val config: SmartConfig) : LifecycleEvent
}