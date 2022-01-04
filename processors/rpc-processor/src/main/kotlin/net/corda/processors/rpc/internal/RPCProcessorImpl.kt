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
import net.corda.schema.Schemas.Config.Companion.CONFIG_TOPIC
import net.corda.v5.base.util.contextLogger
import org.osgi.framework.FrameworkUtil
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.nio.file.Files
import java.nio.file.Path

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
): RPCProcessor {
    private companion object {
        val logger = contextLogger()
    }

    override fun start(config: SmartConfig) {
        configReadService.start()
        configReadService.bootstrapConfig(
            config.withValue("config.topic.name", ConfigValueFactory.fromAnyRef("ConfigTopic"))
        )

        httpRpcGateway.start()

        configRPCOpsService.start()

        val publisherConfig = PublisherConfig("joels-client", 999)
        val publisher = publisherFactory.createPublisher(publisherConfig, config)
        publisher.start()
        val record = Record("ConfigTopic", "corda.rpc", Configuration("""
            address="0.0.0.0:8888"
            context.description="Exposing RPCOps interfaces as OpenAPI WebServices"
            context.title="HTTP RPC demo"
        """.trimIndent(), "999"))
        publisher.publish(listOf(record)).first().get()
    }

    override fun stop() {
        logger.info("RPC processor stopping.")
    }
}