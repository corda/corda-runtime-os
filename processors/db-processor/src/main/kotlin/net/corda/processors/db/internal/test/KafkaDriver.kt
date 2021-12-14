package net.corda.processors.db.internal.test

import com.typesafe.config.ConfigFactory
import io.javalin.Javalin
import net.corda.data.config.ConfigurationManagementRequest
import net.corda.data.config.ConfigurationManagementResponse
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.read.factory.ConfigReaderFactory
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.config.RPCConfig
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.v5.base.util.contextLogger
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory
import org.osgi.framework.FrameworkUtil
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.time.Instant

@Suppress("Unused")
@Component(service = [KafkaDriver::class], immediate = true)
class KafkaDriver @Activate constructor(
    @Reference(service = AvroSchemaRegistry::class)
    private val avroSchemaRegistry: AvroSchemaRegistry,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
    @Reference(service = ConfigReaderFactory::class)
    private val configReaderFactory: ConfigReaderFactory,
    @Reference(service = SmartConfigFactory::class)
    private val smartConfigFactory: SmartConfigFactory
) {
    private companion object {
        val logger = contextLogger()
    }

    private val kafkaConfig = let {
        val configMap = mapOf(
            "messaging.kafka.common.bootstrap.servers" to "kafka:9092",
            "config.topic.name" to "config.topic"
        )
        val config = ConfigFactory.parseMap(configMap)
        smartConfigFactory.create(config)
    }

    private val publisher = publisherFactory.createRPCSender(
        RPCConfig(
            "random_group_name",
            "random_client_name",
            "config-management-request.topic",
            ConfigurationManagementRequest::class.java,
            ConfigurationManagementResponse::class.java
        ),
        kafkaConfig
    ).apply {
        logger.info("JJJ starting publisher.")
        start()
    }

    private var currentConfig: Map<String, SmartConfig> = mapOf()
    private val reader = configReaderFactory.createReader(kafkaConfig).apply {
        logger.info("JJJ starting reader.")
        registerCallback { _, snapshot ->
            logger.info("JJJ in snapshot ${snapshot.keys} ${snapshot.values}")
            currentConfig = snapshot
        }
        start()
    }

    private val server = Javalin
        .create()
        .apply { startServer(this) }
        .get("/sendMessage") { context ->
            val section = "corda.db"
            val configurationString = """{"timestamp": "${Instant.now()}"}"""
            val req = ConfigurationManagementRequest(
                "joel-user",
                Instant.now().toEpochMilli(),
                section,
                configurationString,
                1
            )
            publisher.sendRequest(req).get()

            logger.info("JJJ - Existing entries: $currentConfig")
            val readBackTimestamp = currentConfig[section]?.getString("timestamp")
            context.status(200).result("Current config: $readBackTimestamp")
        }

    init {
        logger.info("JJJ started.")
    }

    private fun startServer(server: Javalin) {
        val bundle = FrameworkUtil.getBundle(WebSocketServletFactory::class.java)

        // We temporarily switch the context class loader to allow Javalin to find `WebSocketServletFactory`.
        val factoryClassLoader = bundle.loadClass(WebSocketServletFactory::class.java.name).classLoader
        val threadClassLoader = Thread.currentThread().contextClassLoader
        try {
            Thread.currentThread().contextClassLoader = factoryClassLoader
            server.start(7777)
        } finally {
            Thread.currentThread().contextClassLoader = threadClassLoader
        }
    }
}