package net.corda.processors.rpc.internal

import net.corda.components.rpc.HttpRpcGateway
import net.corda.configuration.read.ConfigurationReadService
import net.corda.configuration.rpcops.ConfigRPCOpsService
import net.corda.data.config.Configuration
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.processors.rpc.RPCProcessor
import net.corda.schema.Schemas.Config.Companion.CONFIG_TOPIC
import net.corda.schema.configuration.ConfigKeys.Companion.BOOTSTRAP_SERVERS
import net.corda.schema.configuration.ConfigKeys.Companion.RPC_CONFIG
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/** The processor for a `RPCWorker`. */
@Component(service = [RPCProcessor::class])
@Suppress("Unused")
class RPCProcessorImpl @Activate constructor(
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
        val logger = contextLogger()
    }

    override fun start(config: SmartConfig) {
        configReadService.start()
        configReadService.bootstrapConfig(config)

        httpRpcGateway.start()

        configRPCOpsService.start()

        val publisherConfig = PublisherConfig(CLIENT_ID_RPC_PROCESSOR, 1)
        val publisher = publisherFactory.createPublisher(publisherConfig, config)
        publisher.start()
        publisher.use {
            val bootstrapServersConfig = if (config.hasPath(BOOTSTRAP_SERVERS)) {
                val bootstrapServers = config.getString(BOOTSTRAP_SERVERS)
                "\n$BOOTSTRAP_SERVERS=\"$bootstrapServers\""
            } else {
                ""
            }
            val configValue = "$CONFIG_HTTP_RPC$bootstrapServersConfig"

            val record = Record(CONFIG_TOPIC, RPC_CONFIG, Configuration(configValue, "1"))
            publisher.publish(listOf(record)).forEach { future -> future.get() }
        }
    }

    override fun stop() {
        configReadService.stop()
        configRPCOpsService.stop()
        httpRpcGateway.stop()
    }
}