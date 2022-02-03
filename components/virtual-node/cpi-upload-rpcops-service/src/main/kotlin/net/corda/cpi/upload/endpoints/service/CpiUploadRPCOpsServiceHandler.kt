package net.corda.cpi.upload.endpoints.service

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.cpi.upload.endpoints.common.CpiUploadManager
import net.corda.data.chunking.Chunk
import net.corda.data.chunking.ChunkAck
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.*
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger

class CpiUploadRPCOpsServiceHandler(
    private val cpiUploadManager: CpiUploadManager,
    private val configReadService: ConfigurationReadService,
    private val publisherFactory: PublisherFactory
) : LifecycleEventHandler {

    companion object {
        val log = contextLogger()

        val RPC_CONFIG = RPCConfig(
            "dummyGroupName",
            "dummyClientName",
            "dummyRequestTopic",
            Chunk::class.java,
            ChunkAck::class.java
        )
    }

    private var configReadServiceRegistrationHandle: RegistrationHandle? = null
    private var rpcSender: RPCSender<Chunk, ChunkAck>? = null

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
                    log.info("Received ${event.status} event from ConfigurationReadService. Going DOWN as well.")
                    closeResources()
                    coordinator.updateStatus(event.status)
                }
            }
            // On new ConfigChangedEvent we recreate RPCSender? Seems so.
            is ConfigChangedEvent -> {
                event.config[ConfigKeys.RPC_CONFIG]?.let {
                    log.info("Setting CpiUploadManager...")
                    setUpCpiUploadManager(it)
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

    private fun setUpCpiUploadManager(kafkaConfig: SmartConfig) {
        with (cpiUploadManager) {
            if (kafkaConfig.hasPath(ConfigKeys.BOOTSTRAP_SERVERS)) {
                log.info("New RPCSender with new configuration ${ConfigKeys.BOOTSTRAP_SERVERS} will replace the previous one")
                rpcSender?.close()
                rpcSender = publisherFactory.createRPCSender(
                    RPC_CONFIG,
                    kafkaConfig
                )
                setRpcSender(rpcSender!!)
                rpcSender!!.start()
            }

            if (kafkaConfig.hasPath(ConfigKeys.RPC_ENDPOINT_TIMEOUT_MILLIS)) {
                setRpcRequestTimeout(kafkaConfig.getInt(ConfigKeys.RPC_ENDPOINT_TIMEOUT_MILLIS))
            }
        }
    }

    private fun closeResources() {
        rpcSender?.close()
        rpcSender = null
        configReadServiceRegistrationHandle?.close()
        configReadServiceRegistrationHandle = null
    }
}