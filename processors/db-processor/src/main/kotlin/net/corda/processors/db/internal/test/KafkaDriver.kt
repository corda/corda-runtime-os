package net.corda.processors.db.internal.test

import com.typesafe.config.ConfigFactory
import io.javalin.Javalin
import net.corda.data.permissions.management.PermissionManagementRequest
import net.corda.data.permissions.management.PermissionManagementResponse
import net.corda.data.permissions.management.user.CreateUserRequest
import net.corda.libs.configuration.SmartConfigFactory
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
    @Reference(service = SmartConfigFactory::class)
    private val smartConfigFactory: SmartConfigFactory
) {
    private companion object {
        val logger = contextLogger()
    }

    private val kafkaConfig = let {
        val configMap = mapOf("messaging.kafka.common.bootstrap.servers" to "kafka:9092")
        val config = ConfigFactory.parseMap(configMap)
        smartConfigFactory.create(config)
    }

    private val publisher = publisherFactory.createRPCSender(
        RPCConfig(
            "random_group_name",
            "random_client_name",
            "config-update-request",
            PermissionManagementRequest::class.java,
            PermissionManagementResponse::class.java
        ),
        kafkaConfig
    ).apply { start() }

    private val server = Javalin
        .create()
        .apply { startServer(this) }
        .get("/sendMessage") { context ->
            val createUserRequest = CreateUserRequest(
                "", "", false, "", "", Instant.now(), ""
            )
            val req = PermissionManagementRequest("joel=dudley", "", createUserRequest)
            // val bytes = avroSchemaRegistry.serialize(req).array()
            publisher.sendRequest(req)
            context.status(200).result("Done.")
        }

    init {
        logger.info("JJJ started.")
    }

    private fun startServer(server: Javalin) {
        val bundle = FrameworkUtil.getBundle(WebSocketServletFactory::class.java)

        if (bundle == null) {
            server.start(7777)

        } else {
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
}