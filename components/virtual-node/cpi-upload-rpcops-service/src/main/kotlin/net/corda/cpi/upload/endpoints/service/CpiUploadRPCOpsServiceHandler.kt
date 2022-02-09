package net.corda.cpi.upload.endpoints.service

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.chunking.Chunk
import net.corda.data.chunking.ChunkAck
import net.corda.libs.configuration.SmartConfig
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
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger

class CpiUploadRPCOpsServiceHandler(
    private val cpiUploadManagerFactory: CpiUploadManagerFactory,
    private val configReadService: ConfigurationReadService,
    private val publisherFactory: PublisherFactory
) : LifecycleEventHandler {

    companion object {
        val log = contextLogger()
    }

    @VisibleForTesting
    internal var configReadServiceRegistrationHandle: RegistrationHandle? = null
    @VisibleForTesting
    internal var rpcSender: RPCSender<Chunk, ChunkAck>? = null
    internal var cpiUploadManager: CpiUploadManager? = null

    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {

        when (event) {
            is StartEvent -> {
                log.info("Received a start event, waiting until ConfigurationReadService is up")
                configReadServiceRegistrationHandle = coordinator.followStatusChangesByName(
                    setOf(
                        LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
                    )
                )
            }
            is RegistrationStatusChangeEvent -> {
                if (event.status == LifecycleStatus.UP) {
                    log.info("Registering to ConfigurationReadService to receive RPC configuration")
                    configReadService.registerComponentForUpdates(
                        coordinator,
                        setOf(ConfigKeys.RPC_CONFIG)
                    )
                } else {
                    log.info("Received ${event.status} event from ConfigurationReadService. Switching to ${event.status} as well.")
                    closeResources()
                    coordinator.updateStatus(event.status)
                }
            }
            is ConfigChangedEvent -> {
                event.config[ConfigKeys.RPC_CONFIG]?.let {
                    log.info("Setting CpiUploadManager...")
                    rpcSender?.close()
                    cpiUploadManager?.stop()

                    rpcSender = createAndStartRpcSender(it)
                    cpiUploadManager = createAndStartCpiUploadManager(it, rpcSender!!)

                    coordinator.updateStatus(LifecycleStatus.UP)
                }
                    // Should we throw here or send a StopEvent?
                    ?: throw CordaRuntimeException("Expected RPC configuration")
            }
            is StopEvent -> {
                closeResources()
                coordinator.updateStatus(LifecycleStatus.DOWN)
            }
        }
    }

    private fun createAndStartRpcSender(config: SmartConfig): RPCSender<Chunk, ChunkAck> {
        val rpcConfig = RPCConfig(
            "todo",
            "todo",
            CPI_UPLOAD_TOPIC,
            Chunk::class.java,
            ChunkAck::class.java
        )
        return publisherFactory.createRPCSender(rpcConfig, config)
            .also { it.start() }
    }

    private fun createAndStartCpiUploadManager(smartConfig: SmartConfig, rpcSender: RPCSender<Chunk, ChunkAck>): CpiUploadManager {
        return cpiUploadManagerFactory.create(smartConfig, rpcSender)
            .also { it.start() }
    }

    private fun closeResources() {
        rpcSender?.close()
        rpcSender = null
        configReadServiceRegistrationHandle?.close()
        configReadServiceRegistrationHandle = null
        cpiUploadManager?.close()
        cpiUploadManager = null
    }
}