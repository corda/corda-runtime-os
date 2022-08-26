package net.corda.chunking.read.impl

import net.corda.chunking.db.ChunkDbWriterFactory
import net.corda.chunking.read.ChunkReadService
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.cpiinfo.write.CpiInfoWriteService
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.libs.configuration.helper.getConfig
import net.corda.lifecycle.DependentComponents
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.virtualnode.rpcops.virtualNodeManagementSender.VirtualNodeManagementSenderFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import java.time.Duration

@Suppress("UNUSED", "LongParameterList")
@Component(service = [ChunkReadService::class])
class ChunkReadServiceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ChunkDbWriterFactory::class)
    private val chunkDbWriterFactory: ChunkDbWriterFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = DbConnectionManager::class)
    private val dbConnectionManager: DbConnectionManager,
    @Reference(service = CpiInfoWriteService::class)
    private val cpiInfoWriteService: CpiInfoWriteService,
    @Reference(service = VirtualNodeManagementSenderFactory::class)
    private val virtualNodeManagementSenderFactory: VirtualNodeManagementSenderFactory
) : ChunkReadService, LifecycleEventHandler {
    companion object {
        val log: Logger = contextLogger()
        private val requiredKeys = setOf(ConfigKeys.BOOT_CONFIG, ConfigKeys.MESSAGING_CONFIG, ConfigKeys.RPC_CONFIG)

        private const val REGISTRATION = "REGISTRATION"
        private const val CONFIG_HANDLE = "CONFIG_HANDLE"
    }

    private val coordinator = coordinatorFactory.createCoordinator<ChunkReadService>(this)
    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> onStartEvent(coordinator)
            is StopEvent -> onStopEvent(coordinator)
            is ConfigChangedEvent -> onConfigChangedEvent(event, coordinator)
            is RegistrationStatusChangeEvent -> onRegistrationStatusChangeEvent(event)
        }
    }

    override val isRunning get() = coordinator.isRunning

    override fun start() = coordinator.start()

    override fun stop() = coordinator.stop()

    private val dependentComponents = DependentComponents.of(
        ::configurationReadService,
        ::cpiInfoWriteService,
        ::dbConnectionManager
    )

    private fun onStartEvent(coordinator: LifecycleCoordinator) {
        log.debug("onStartEvent")

        coordinator.createManagedResource(REGISTRATION) {
            coordinator.followStatusChangesByName(
                setOf(
                    LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
                    LifecycleCoordinatorName.forComponent<DbConnectionManager>(),
                    LifecycleCoordinatorName.forComponent<CpiInfoWriteService>()
                )
            )
        }

        dependentComponents.registerAndStartAll(coordinator)
        coordinator.updateStatus(LifecycleStatus.UP)
    }

    private fun onStopEvent(coordinator: LifecycleCoordinator) {
        log.debug("onStopEvent")
        coordinator.closeManagedResources()
        coordinator.updateStatus(LifecycleStatus.DOWN)
    }

    private fun onRegistrationStatusChangeEvent(event: RegistrationStatusChangeEvent) {
        log.debug("onRegistrationStatusChangeEvent")
        log.info("${event.status}")

        when (event.status) {
            LifecycleStatus.UP -> {
                coordinator.createManagedResource(CONFIG_HANDLE) {
                    configurationReadService.registerComponentForUpdates(
                        coordinator,
                        requiredKeys
                    )
                }
            }
            LifecycleStatus.ERROR -> {
                coordinator.closeManagedResources(setOf(CONFIG_HANDLE))
                coordinator.postEvent(StopEvent(errored = true))
            }
            else -> log.debug { "Unexpected status: ${event.status}" }
        }
    }

    private fun onConfigChangedEvent(event: ConfigChangedEvent, coordinator: LifecycleCoordinator) {
        log.debug("onConfigChangedEvent")
        if (requiredKeys.all { it in event.config.keys } and event.keys.any { it in requiredKeys }) {
            val bootConfig = event.config.getConfig(ConfigKeys.BOOT_CONFIG)
            val messagingConfig = event.config.getConfig(ConfigKeys.MESSAGING_CONFIG)
            val rpcConfig = event.config.getConfig(ConfigKeys.RPC_CONFIG)
            val duration = Duration.ofMillis(rpcConfig.getInt(ConfigKeys.RPC_ENDPOINT_TIMEOUT_MILLIS).toLong())
            coordinator.createManagedResource("CHUNK_DB_WRITER") {
                chunkDbWriterFactory
                    .create(
                        messagingConfig,
                        bootConfig,
                        dbConnectionManager.getClusterEntityManagerFactory(),
                        cpiInfoWriteService,
                        virtualNodeManagementSenderFactory.createSender(duration, messagingConfig)
                    )
                    .apply { start() }
            }

            coordinator.updateStatus(LifecycleStatus.UP)
        }
    }

    override fun close() {
        coordinator.close()
    }
}
