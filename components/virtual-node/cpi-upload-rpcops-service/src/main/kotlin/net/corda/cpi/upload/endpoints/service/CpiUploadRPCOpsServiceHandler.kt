package net.corda.cpi.upload.endpoints.service

import com.typesafe.config.ConfigFactory
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.chunking.Chunk
import net.corda.data.chunking.ChunkAck
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.cpiupload.CpiUploadManager
import net.corda.libs.cpiupload.CpiUploadManagerFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.schema.Schemas.VirtualNode.Companion.CPI_UPLOAD_TOPIC
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.v5.base.util.contextLogger

/**
 * Registers to [ConfigurationReadService] for config updates, and on new config updates creates a new [CpiUploadManager]
 * which also exposes.
 */
class CpiUploadRPCOpsServiceHandler(
    private val cpiUploadManagerFactory: CpiUploadManagerFactory,
    private val configReadService: ConfigurationReadService,
    private val publisherFactory: PublisherFactory
) : LifecycleEventHandler {

    companion object {
        val log = contextLogger()

        const val CPI_UPLOAD_GROUP = "cpi.uploader"
        const val CPI_UPLOAD_CLIENT_NAME = "$CPI_UPLOAD_GROUP.rpc"
    }

    @VisibleForTesting
    internal var configReadServiceRegistrationHandle: RegistrationHandle? = null

    @VisibleForTesting
    internal var rpcSender: RPCSender<Chunk, ChunkAck>? = null
    internal var cpiUploadManager: CpiUploadManager? = null

    private var previousRpcConfig: SmartConfig? =
        SmartConfigFactory.create(ConfigFactory.empty()).create(ConfigFactory.empty())
    private var configSubscription: AutoCloseable? = null

    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> onStartEvent(coordinator)
            is RegistrationStatusChangeEvent -> onRegistrationStatusChangeEvent(event, coordinator)
            is ConfigChangedEvent -> onConfigChangedEvent(event, coordinator)
            is StopEvent -> onStopEvent(coordinator)
        }
    }

    private fun onStartEvent(coordinator: LifecycleCoordinator) {
        log.info("CPI Upload RPCOpsServiceHandler event - start")

        configReadServiceRegistrationHandle?.close()
        configReadServiceRegistrationHandle = coordinator.followStatusChangesByName(
            setOf(
                LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
            )
        )
    }

    private fun onStopEvent(coordinator: LifecycleCoordinator) {
        log.info("CPI Upload RPCOpsServiceHandler event - stop")

        closeResources()
        coordinator.updateStatus(LifecycleStatus.DOWN)
    }

    private fun onRegistrationStatusChangeEvent(
        event: RegistrationStatusChangeEvent,
        coordinator: LifecycleCoordinator
    ) {
        log.info("CPI Upload RPCOpsServiceHandler event - registration status changed")

        if (event.status == LifecycleStatus.UP) {
            log.info("Registering to ConfigurationReadService to receive RPC configuration")
            configSubscription = configReadService.registerComponentForUpdates(
                coordinator,
                setOf(
                    //ConfigKeys.MESSAGING_CONFIG,  // TODO:  uncomment when MESSAGING key is used
                    ConfigKeys.BOOT_CONFIG,
                    ConfigKeys.RPC_CONFIG
                )
            )
        } else {
            log.info("Received ${event.status} event from ConfigurationReadService. Switching to ${event.status} as well.")
            closeResources()
            coordinator.updateStatus(event.status)
        }
    }

    private fun onConfigChangedEvent(
        event: ConfigChangedEvent,
        coordinator: LifecycleCoordinator
    ) {
        log.info("CPI Upload RPCOpsServiceHandler event - config changed")

        if (!event.config.containsKey(ConfigKeys.RPC_CONFIG) || !event.config.containsKey(ConfigKeys.BOOT_CONFIG)) {
            log.info("CPI Upload RPCOpsServiceHandler event - config changed - only have keys  ${event.config.keys}")
            return
        }

        // RPC_CONFIG is not currently being used (in `CpiUploadManagerImpl`).
        val rpcConfig = event.config[ConfigKeys.RPC_CONFIG]?.also { previousRpcConfig = it } ?: previousRpcConfig

        // val messagingConfig = event.config.toMessagingConfig()  // TODO:  uncomment when MESSAGING key is used
        val messagingConfig = event.config[ConfigKeys.BOOT_CONFIG]!!

        rpcSender?.close()
        rpcSender = createAndStartRpcSender(messagingConfig)
        cpiUploadManager = createAndStartCpiUploadManager(rpcConfig!!, rpcSender!!)

        coordinator.updateStatus(LifecycleStatus.UP)
    }

    private fun createAndStartRpcSender(config: SmartConfig): RPCSender<Chunk, ChunkAck> {
        val rpcConfig = RPCConfig(
            CPI_UPLOAD_GROUP,
            CPI_UPLOAD_CLIENT_NAME,
            CPI_UPLOAD_TOPIC,
            Chunk::class.java,
            ChunkAck::class.java
        )
        return publisherFactory.createRPCSender(rpcConfig, config)
            .also { it.start() }
    }

    private fun createAndStartCpiUploadManager(
        smartConfig: SmartConfig,
        rpcSender: RPCSender<Chunk, ChunkAck>
    ): CpiUploadManager {
        return cpiUploadManagerFactory.create(smartConfig, rpcSender)
    }

    private fun closeResources() {
        rpcSender?.close()
        rpcSender = null
        configReadServiceRegistrationHandle?.close()
        configReadServiceRegistrationHandle = null
        configSubscription?.close()
        configSubscription = null
        cpiUploadManager = null
    }
}
