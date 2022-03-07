package net.corda.cpk.read.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.cpk.read.CpkReadService
import net.corda.cpk.read.impl.services.CpkChunksKafkaReader
import net.corda.cpk.read.impl.services.persistence.CpkChunksFileManagerImpl
import net.corda.libs.configuration.SmartConfig
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
import net.corda.messaging.api.config.toMessagingConfig
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import java.nio.file.Paths

@Component(service = [CpkReadService::class])
class CpkReadServiceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configReadService: ConfigurationReadService,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory
) : CpkReadService, LifecycleEventHandler {
    companion object {
        val logger: Logger = contextLogger()

        const val CPK_READ_GROUP = "cpk.reader"
        const val CPK_READ_CLIENT = "$CPK_READ_GROUP.client"
    }

    private val coordinator = coordinatorFactory.createCoordinator<CpkReadService>(this)

    @VisibleForTesting
    internal var configReadServiceRegistration: RegistrationHandle? = null
    @VisibleForTesting
    internal var configSubscription: AutoCloseable? = null
    @VisibleForTesting
    internal var cpkChunksKafkaReaderSubscription: AutoCloseable? = null

//    private var reader: CpkFileReader? = null // not used yet

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
                        " Component ${this::class.java.simpleName} is not started"
            )
            closeResources()
        }
    }

    private fun onConfigChangedEvent(event: ConfigChangedEvent, coordinator: LifecycleCoordinator) {
        logger.info("Configuring CPK Read Service")
        val config = event.config.toMessagingConfig()
        // TODO fix configuration below

        createCpkChunksKafkaReader(config)
        coordinator.updateStatus(LifecycleStatus.UP)
    }

    /**
     * Close the registration.
     */
    private fun onStopEvent() {
        closeResources()
    }

    override fun close() {
        closeResources()
    }

//    override fun get(cpkMetadata: CPK.Metadata): CPK? {
//        if (reader == null) {
//            throw CordaRuntimeException("CpkReadServiceImpl has not been initialised yet")
//        }
//        return reader!!.get(cpkMetadata)
//    }

    private fun createCpkChunksKafkaReader(config: SmartConfig) {
        val cpkChunksFileManager = CpkChunksFileManagerImpl(Paths.get("/temp"))
        cpkChunksKafkaReaderSubscription?.close()
        cpkChunksKafkaReaderSubscription = subscriptionFactory.createCompactedSubscription(
            SubscriptionConfig(CPK_READ_GROUP, Schemas.VirtualNode.CPK_FILE_TOPIC),
            CpkChunksKafkaReader(cpkChunksFileManager),
            config
        ).also { it.start() }
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
