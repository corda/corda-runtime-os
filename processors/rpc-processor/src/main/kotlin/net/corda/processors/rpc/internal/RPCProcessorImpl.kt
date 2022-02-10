package net.corda.processors.rpc.internal

import net.corda.components.rpc.HttpRpcGateway
import net.corda.configuration.read.ConfigurationReadService
import net.corda.configuration.rpcops.ConfigRPCOpsService
import net.corda.cpi.upload.endpoints.service.CpiUploadRPCOpsService
import net.corda.data.config.Configuration
import net.corda.flow.rpcops.FlowRPCOpsService
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.DependentComponents
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.processors.rpc.RPCProcessor
import net.corda.schema.Schemas.Config.Companion.CONFIG_TOPIC
import net.corda.schema.configuration.ConfigKeys.BOOTSTRAP_SERVERS
import net.corda.schema.configuration.ConfigKeys.RPC_CONFIG
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.virtualnode.rpcops.VirtualNodeRPCOpsService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/** The processor for a `RPCWorker`. */
@Component(service = [RPCProcessor::class])
@Suppress("Unused", "LongParameterList")
class RPCProcessorImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configReadService: ConfigurationReadService,
    @Reference(service = ConfigRPCOpsService::class)
    private val configRPCOpsService: ConfigRPCOpsService,
    @Reference(service = HttpRpcGateway::class)
    private val httpRpcGateway: HttpRpcGateway,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
    @Reference(service = VirtualNodeRPCOpsService::class)
    private val virtualNodeRPCOpsService: VirtualNodeRPCOpsService,
    @Reference(service = FlowRPCOpsService::class)
    private val flowRPCOpsService: FlowRPCOpsService,
    @Reference(service = CpiUploadRPCOpsService::class)
    private val cpiUploadRPCOpsService: CpiUploadRPCOpsService
) : RPCProcessor {

    private companion object {
        val log = contextLogger()
    }

    private val lifecycleCoordinator = coordinatorFactory.createCoordinator<RPCProcessorImpl>(::eventHandler)
    private val dependentComponents = DependentComponents.of(
        ::configReadService,
        ::httpRpcGateway,
        ::flowRPCOpsService,
        ::configRPCOpsService,
        ::virtualNodeRPCOpsService,
        ::cpiUploadRPCOpsService
    )

    override fun start(bootConfig: SmartConfig) {
        log.info("RPC processor starting.")
        lifecycleCoordinator.start()
        lifecycleCoordinator.postEvent(BootConfigEvent(bootConfig))
    }

    override fun stop() {
        log.info("RPC processor stopping.")
        lifecycleCoordinator.stop()
    }

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        log.debug { "RPC processor received event $event." }
        when (event) {
            is StartEvent -> {
                dependentComponents.registerAndStartAll(coordinator)
            }
            is RegistrationStatusChangeEvent -> {
                log.info("RPC processor is ${event.status}")
                coordinator.updateStatus(event.status)
            }
            is BootConfigEvent -> {
                configReadService.bootstrapConfig(event.config)

                val publisherConfig = PublisherConfig(CLIENT_ID_RPC_PROCESSOR, 1)
                val publisher = publisherFactory.createPublisher(publisherConfig, event.config)
                publisher.start()
                publisher.use {
                    val bootstrapServersConfig = if (event.config.hasPath(BOOTSTRAP_SERVERS)) {
                        val bootstrapServers = event.config.getString(BOOTSTRAP_SERVERS)
                        "\n$BOOTSTRAP_SERVERS=\"$bootstrapServers\""
                    } else {
                        ""
                    }
                    val configValue = "$CONFIG_HTTP_RPC$bootstrapServersConfig"

                    val record = Record(CONFIG_TOPIC, RPC_CONFIG, Configuration(configValue, "1"))
                    publisher.publish(listOf(record)).forEach { future -> future.get() }
                }
            }
            is StopEvent -> {
                dependentComponents.stopAll()
            }
            else -> {
                log.error("Unexpected event $event!")
            }
        }
    }
}

data class BootConfigEvent(val config: SmartConfig) : LifecycleEvent