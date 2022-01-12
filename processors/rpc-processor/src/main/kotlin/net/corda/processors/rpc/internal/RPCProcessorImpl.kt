package net.corda.processors.rpc.internal

import com.typesafe.config.ConfigValueFactory
import net.corda.components.rpc.HttpRpcGateway
import net.corda.configuration.read.ConfigurationReadService
import net.corda.configuration.rpcops.ConfigRPCOpsService
import net.corda.data.config.Configuration
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.processors.rpc.RPCProcessor
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
        configReadService.bootstrapConfig(
            config.withValue(CONFIG_KEY_CONFIG_TOPIC_NAME, ConfigValueFactory.fromAnyRef(CONFIG_TOPIC))
        )

        httpRpcGateway.start()

        configRPCOpsService.start()

        val publisherConfig = PublisherConfig(CLIENT_ID_RPC_PROCESSOR, 1)
        val publisher = publisherFactory.createPublisher(publisherConfig, config)
        publisher.start()

        val bootstrapServersConfig = if (config.hasPath(CONFIG_KEY_BOOTSTRAP_SERVERS)) {
            val bootstrapServers = config.getString(CONFIG_KEY_BOOTSTRAP_SERVERS)
            "\n$CONFIG_KEY_BOOTSTRAP_SERVERS=\"$bootstrapServers\""
        } else {
            ""
        }
        val configValue = "$CONFIG_HTTP_RPC\n$CONFIG_CONFIG_MGMT_REQUEST_TIMEOUT$bootstrapServersConfig"

        val record = Record(CONFIG_TOPIC, RPC_CONFIG, Configuration(configValue, "1"))
        publisher.publish(listOf(record)).forEach { future -> future.get() }
    }

    override fun stop() {
        logger.info("RPC processor stopping.")
    }
}