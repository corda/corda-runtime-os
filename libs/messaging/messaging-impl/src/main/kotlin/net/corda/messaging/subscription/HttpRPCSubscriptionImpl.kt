package net.corda.messaging.subscription

import java.util.UUID
import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializer
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


/**
 * Implementation of a RPCSubscription
 *
 * This subscription will register and listen to an endpoint that will be registered to
 * the webserver on subscription start
 *
 *
 * @param REQUEST the request Type to be deserialized
 * @param RESPONSE the response Type to be serialized
 * @property rpcConfig the config object that contains endpoint for the subscription to listen on
 * @property processor processes incoming requests. Produces an output of RESPONSE.
 * @property lifecycleCoordinatorFactory
 * @property webServer webserver component
 * @property cordaAvroSerializer serializer for the RESPONSE type
 * @property cordaAvroDeserializer deserializer for the REQUEST type
 */
@Suppress("LongParameterList")
internal class HttpRPCSubscriptionImpl<REQUEST : Any, RESPONSE : Any>(
    private val rpcConfig: HttpRPCConfig,
    val processor: HttpRPCProcessor<REQUEST, RESPONSE>,
    val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    val webServer: WebServer,
    val cordaAvroSerializer: CordaAvroSerializer<RESPONSE>,
    val cordaAvroDeserializer: CordaAvroDeserializer<REQUEST>
) : RPCSubscription<REQUEST, RESPONSE> {

    private lateinit var endpoint: Endpoint
    override val subscriptionName =
        LifecycleCoordinatorName(
            "RPCSubscription-${rpcConfig.endpoint.removePrefix("/")}-${UUID.randomUUID()}"
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

        val webHandler = object : WebHandler {
            override fun handle(context: WebContext): WebContext {
                val payload = cordaAvroDeserializer.deserialize(context.bodyAsBytes())

                if (payload != null) {
                    val serializedResponse = cordaAvroSerializer.serialize(processor.process(payload))
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