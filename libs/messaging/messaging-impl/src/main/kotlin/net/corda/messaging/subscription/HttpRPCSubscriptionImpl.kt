package net.corda.messaging.subscription

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.messaging.api.processor.HttpRPCProcessor
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.config.HttpRPCConfig
import net.corda.rest.ResponseCode
import net.corda.web.api.Endpoint
import net.corda.web.api.HTTPMethod
import net.corda.web.api.WebContext
import net.corda.web.api.WebHandler
import net.corda.web.api.WebServer
import org.slf4j.LoggerFactory


internal class HttpRPCSubscriptionImpl<REQUEST : Any, RESPONSE : Any>(
    private val rpcConfig: HttpRPCConfig<REQUEST, RESPONSE>,
    val processor: HttpRPCProcessor<REQUEST, RESPONSE>,
    val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    val cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    val webServer: WebServer
) : RPCSubscription<REQUEST, RESPONSE> {

    private lateinit var endpoint: Endpoint
    override val subscriptionName =
        LifecycleCoordinatorName(
            "${rpcConfig.groupName}-RPCSubscription-${rpcConfig.endpoint.removePrefix("/")}"
        )


    private val coordinator = lifecycleCoordinatorFactory.createCoordinator(subscriptionName) { _, _ -> }

    override fun start() {
        registerEndpoint(rpcConfig.endpoint, processor)
        coordinator.start()
        coordinator.updateStatus(LifecycleStatus.UP)
    }

    override fun close() {
        webServer.removeEndpoint(endpoint)
        coordinator.updateStatus(LifecycleStatus.DOWN)
        coordinator.close()
    }

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private fun registerEndpoint(
        rpcEndpoint: String,
        processor: HttpRPCProcessor<REQUEST, RESPONSE>
    ) {
        val server = webServer

        val avroDeserializer = cordaAvroSerializationFactory.createAvroDeserializer({
            log.error("Failed to deserialize payload for request")
        }, processor.reqClazz)

        val avroSerializer = cordaAvroSerializationFactory.createAvroSerializer<RESPONSE> {
            log.error("Failed to serialize payload for response")
        }

        val webHandler = object : WebHandler {
            override fun handle(context: WebContext): WebContext {
                val payload = avroDeserializer.deserialize(context.bodyAsBytes())

                if (payload != null) {
                    val serializedResponse = avroSerializer.serialize(processor.process(payload))
                    return if (serializedResponse != null) {
                        context.result(serializedResponse)
                        context
                    } else {
                        log.error("Response Payload was Null")
                        context.result("Response Payload was Null")
                        context.status(ResponseCode.UNPROCESSABLE_CONTENT.statusCode)
                        context
                    }
                } else {
                    log.error("Request Payload was Null")
                    context.result("Request Payload was Null")
                    context.status(ResponseCode.UNPROCESSABLE_CONTENT.statusCode)
                    return context
                }
            }
        }
        val addedEndpoint = Endpoint(HTTPMethod.POST, rpcEndpoint, webHandler)
        server.registerEndpoint(addedEndpoint)
        endpoint = addedEndpoint
    }
}