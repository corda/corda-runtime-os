package net.corda.processors.rpc.internal

import net.corda.components.rpc.HttpRpcGateway
import net.corda.configuration.read.ConfigurationReadService
import net.corda.configuration.rpcops.ConfigRPCOpsService
import net.corda.data.config.Configuration
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.processors.rpc.RPCProcessor
import net.corda.schema.Schemas.Config.Companion.CONFIG_TOPIC
import net.corda.schema.configuration.ConfigKeys.Companion.BOOTSTRAP_SERVERS
import net.corda.schema.configuration.ConfigKeys.Companion.RPC_CONFIG
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/** The processor for a `RPCWorker`. */
@Component(service = [RPCProcessor::class])
@Suppress("Unused")
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
    private val publisherFactory: PublisherFactory
) : RPCProcessor {

    private companion object {
        val log = contextLogger()
    }

    private val lifecycleCoordinator = coordinatorFactory.createCoordinator<RPCProcessorImpl>(::eventHandler)

    override fun start(bootConfig: SmartConfig) {
        log.info("RPC processor starting.")
        lifecycleCoordinator.start()
        lifecycleCoordinator.postEvent(BootConfigEvent(bootConfig))
    }

    override fun stop() {
        log.info("RPC processor stopping.")
        lifecycleCoordinator.stop()
    }

    @Suppress("UNUSED_PARAMETER")
    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        log.debug { "RPC processor received event $event." }
        when (event) {
            is StartEvent -> {
                configReadService.start()
                httpRpcGateway.start()
                configRPCOpsService.start()
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
                configReadService.stop()
                configRPCOpsService.stop()
                httpRpcGateway.stop()
            }
            else -> {
                log.error("Unexpected event $event!")
            }
        }
    }
}

data class BootConfigEvent(val config: SmartConfig) : LifecycleEvent