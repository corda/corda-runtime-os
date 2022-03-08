package net.corda.chunking.read.impl

import net.corda.chunking.db.ChunkDbWriter
import net.corda.chunking.db.ChunkDbWriterFactory
import net.corda.chunking.read.ChunkReadService
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.cpiinfo.write.CpiInfoWriteService
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger

@Suppress("UNUSED")
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
    private val cpiInfoWriteService: CpiInfoWriteService
) : ChunkReadService, LifecycleEventHandler {
    companion object {
        val log: Logger = contextLogger()
    }

    private val coordinator = coordinatorFactory.createCoordinator<ChunkReadService>(this)

    private var chunkDbWriter: ChunkDbWriter? = null
    private var registration: RegistrationHandle? = null
    private var configSubscription: AutoCloseable? = null

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

    private fun onStartEvent(coordinator: LifecycleCoordinator) {
        log.debug("onStartEvent")

        configurationReadService.start()
        cpiInfoWriteService.start()

        registration?.close()
        registration =
            coordinator.followStatusChangesByName(
                setOf(
                    LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
                    LifecycleCoordinatorName.forComponent<DbConnectionManager>(),
                    LifecycleCoordinatorName.forComponent<CpiInfoWriteService>()
                )
            )
    }

    private fun onStopEvent(coordinator: LifecycleCoordinator) {
        log.debug("onStopEvent")

        chunkDbWriter?.stop()
        chunkDbWriter = null

        coordinator.updateStatus(LifecycleStatus.DOWN)
    }

    private fun onRegistrationStatusChangeEvent(event: RegistrationStatusChangeEvent) {
        log.debug("onRegistrationStatusChangeEvent")

        if (event.status == LifecycleStatus.UP) {
            configSubscription =
                configurationReadService.registerComponentForUpdates(
                    coordinator, setOf(
                        ConfigKeys.BOOT_CONFIG,
                        //ConfigKeys.MESSAGING_CONFIG,  // TODO - uncomment when 'messaging' is sorted out.
                    )
                )
        } else {
            configSubscription?.close()
            coordinator.updateStatus(LifecycleStatus.DOWN)
        }
    }

    private fun onConfigChangedEvent(event: ConfigChangedEvent, coordinator: LifecycleCoordinator) {
        log.debug("onConfigChangedEvent")

        //val config = event.config.toMessagingConfig()  // TODO - uncomment when 'messaging' is sorted out.
        val config = event.config[ConfigKeys.BOOT_CONFIG]!!
        chunkDbWriter?.close()
        chunkDbWriter = chunkDbWriterFactory
            .create(config, dbConnectionManager.getClusterEntityManagerFactory(), cpiInfoWriteService)
            .apply { start() }

        coordinator.updateStatus(LifecycleStatus.UP)
    }

    override fun close() {
        configSubscription?.close()
        registration?.close()
        chunkDbWriter?.close()
    }
}
