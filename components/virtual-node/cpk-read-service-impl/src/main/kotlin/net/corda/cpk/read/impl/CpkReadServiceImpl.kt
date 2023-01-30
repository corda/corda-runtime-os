package net.corda.cpk.read.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.cpk.read.CpkReadService
import net.corda.cpk.read.impl.services.CpkChunksKafkaReader
import net.corda.cpk.read.impl.services.persistence.CpkChunksFileManagerImpl
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.helper.getConfig
import net.corda.libs.packaging.Cpk
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
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.utilities.PathProvider
import net.corda.utilities.TempPathProvider
import net.corda.utilities.VisibleForTesting
import net.corda.utilities.WorkspacePathProvider
import net.corda.v5.base.util.debug
import net.corda.v5.crypto.SecureHash
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

@Component(service = [CpkReadService::class])
class CpkReadServiceImpl (
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val configReadService: ConfigurationReadService,
    private val subscriptionFactory: SubscriptionFactory,
    private val workspacePathProvider: PathProvider,
    private val tempPathProvider: PathProvider
) : CpkReadService, LifecycleEventHandler {

    @Activate
    constructor(
        @Reference(service = LifecycleCoordinatorFactory::class)
        coordinatorFactory: LifecycleCoordinatorFactory,
        @Reference(service = ConfigurationReadService::class)
        configReadService: ConfigurationReadService,
        @Reference(service = SubscriptionFactory::class)
        subscriptionFactory: SubscriptionFactory
    ): this(
        coordinatorFactory,
        configReadService,
        subscriptionFactory,
        WorkspacePathProvider(),
        TempPathProvider()
    )

    companion object {
        val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

        const val CPK_CACHE_DIR = "cpk-cache"
        const val CPK_PARTS_DIR = "cpk-parts"
        const val CPK_READ_GROUP = "cpk.reader"
    }

    private val coordinator = coordinatorFactory.createCoordinator<CpkReadService>(this)

    @VisibleForTesting
    internal var configReadServiceRegistration: RegistrationHandle? = null
    @VisibleForTesting
    internal var configSubscription: AutoCloseable? = null
    @VisibleForTesting
    internal var cpkChunksKafkaReaderSubscription: AutoCloseable? = null

    private val cpksByChecksum = ConcurrentHashMap<SecureHash, Cpk>()

    /**
     * Event loop
     */
    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> onStartEvent(coordinator)
            is RegistrationStatusChangeEvent -> onRegistrationStatusChangeEvent(event, coordinator)
            is ConfigChangedEvent -> onConfigChangedEvent(event, coordinator)
            is StopEvent -> onStopEvent()
        }
    }

    private fun onStartEvent(coordinator: LifecycleCoordinator) {
        configReadServiceRegistration?.close()
        configReadServiceRegistration =
            coordinator.followStatusChangesByName(setOf(LifecycleCoordinatorName.forComponent<ConfigurationReadService>()))
    }

    private fun onRegistrationStatusChangeEvent(event: RegistrationStatusChangeEvent, coordinator: LifecycleCoordinator) {
        if (event.status == LifecycleStatus.UP) {
            configSubscription?.close()
            configSubscription = configReadService.registerComponentForUpdates(
                coordinator,
                setOf(
                    ConfigKeys.BOOT_CONFIG,
                    ConfigKeys.MESSAGING_CONFIG
                )
            )
        } else {
            logger.warn(
                "Received a ${RegistrationStatusChangeEvent::class.java.simpleName} with status ${event.status}." +
                        " Component ${this::class.java.simpleName} is not started."
            )
            closeResources()
        }
    }

    private fun onConfigChangedEvent(event: ConfigChangedEvent, coordinator: LifecycleCoordinator) {
        logger.info("Configuring CPK Read Service")
        createCpkChunksKafkaReader(event.config.getConfig(MESSAGING_CONFIG), event.config.getConfig(BOOT_CONFIG))
        coordinator.updateStatus(LifecycleStatus.UP)
    }

    /**
     * Close the registration.
     */
    private fun onStopEvent() {
        closeResources()
    }

    @Deactivate
    fun close() {
        closeResources()
    }

    override fun get(cpkFileChecksum: SecureHash): Cpk? =
        cpksByChecksum[cpkFileChecksum]

    private fun createCpkChunksKafkaReader(messagingConfig: SmartConfig, bootConfig: SmartConfig) {
        val (cpkCacheDir, cpkPartsDir) = try {
            workspacePathProvider.getOrCreate(bootConfig, CPK_CACHE_DIR) to
                    tempPathProvider.getOrCreate(bootConfig, CPK_PARTS_DIR)
        } catch (e: Exception) {
            logger.error("Error while trying to create directories. Component shuts down.", e)
            closeResources()
            return
        }

        val cpkChunksFileManager = CpkChunksFileManagerImpl(cpkCacheDir)
        cpkChunksKafkaReaderSubscription?.close()
        cpkChunksKafkaReaderSubscription =
            subscriptionFactory.createCompactedSubscription(
                SubscriptionConfig(CPK_READ_GROUP, Schemas.VirtualNode.CPK_FILE_TOPIC),
                CpkChunksKafkaReader(cpkPartsDir, cpkChunksFileManager, this::onCpkAssembled),
                messagingConfig
            ).also { it.start() }
    }

    private fun onCpkAssembled(cpkFileChecksum: SecureHash, cpk: Cpk) {
        logger.info("${this::class.java.simpleName} storing:  $cpkFileChecksum")
        cpksByChecksum[cpkFileChecksum] = cpk
    }

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        logger.debug { "CPK Read Service starting" }
        coordinator.start()
    }

    override fun stop() {
        logger.debug { "CPK Read Service stopping" }
        coordinator.stop()
    }

    private fun closeResources() {
        configReadServiceRegistration?.close()
        configReadServiceRegistration = null
        configSubscription?.close()
        configSubscription = null
        cpkChunksKafkaReaderSubscription?.close()
        cpkChunksKafkaReaderSubscription = null
    }
}
