package net.corda.messaging.subscription

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.messaging.api.exception.CordaHTTPServerTransientException
import net.corda.messaging.api.processor.SyncRPCProcessor
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.config.SyncRPCConfig
import net.corda.rest.ResponseCode
import net.corda.web.api.Endpoint
import net.corda.web.api.HTTPMethod
import net.corda.web.api.WebContext
import net.corda.web.api.WebHandler
import net.corda.web.api.WebServer
import org.slf4j.LoggerFactory
import java.util.*

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
internal class SyncRPCSubscriptionImpl<REQUEST : Any, RESPONSE : Any>(
    private val rpcConfig: SyncRPCConfig,
    private val processor: SyncRPCProcessor<REQUEST, RESPONSE>,
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    private val webServer: WebServer,
    private val cordaAvroSerializer: CordaAvroSerializer<RESPONSE>,
    private val cordaAvroDeserializer: CordaAvroDeserializer<REQUEST>,
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
        processor: SyncRPCProcessor<REQUEST, RESPONSE>,
    ) {
        val server = webServer

        val webHandler = WebHandler { context ->
            val payload = cordaAvroDeserializer.deserialize(context.bodyAsBytes())

            if (payload == null) {
                log.warn("Request Payload was invalid")
                context.result("Request Payload was invalid")
                context.status(ResponseCode.BAD_REQUEST)
                return@WebHandler context
            }

            val response = try {
                processor.process(payload)
            } catch (ex: Exception) {
                return@WebHandler handleProcessorException(endpoint, ex, context)
            }

            // assume a null response is no response and return a zero length byte array
            if (response == null) {
                context.result(ByteArray(0))
            } else {
                val serializedResponse = cordaAvroSerializer.serialize(response)
                if (serializedResponse != null) {
                    context.result(serializedResponse)
                } else {
                    val errorMsg = "Response Payload cannot be serialised: ${response.javaClass.name}"
                    log.warn(errorMsg)
                    context.result(errorMsg)
                    context.status(ResponseCode.INTERNAL_SERVER_ERROR)
                }
            }
            context
        }

        val addedEndpoint = Endpoint(HTTPMethod.POST, rpcEndpoint, webHandler, true)
        server.registerEndpoint(addedEndpoint)
        endpoint = addedEndpoint
    }

    private fun handleProcessorException(
        endpoint: Endpoint,
        ex: Exception,
        context: WebContext
    ): WebContext {
        when (ex) {
            is CordaHTTPServerTransientException -> {
                "Transient error processing RPC request for $endpoint: ${ex.message}".also { msg ->
                    log.warn(msg, ex)
                    context.result(msg)
                }
                context.status(ResponseCode.SERVICE_UNAVAILABLE)
            }

            else -> {
                "Failed to process RPC request for $endpoint".also { message ->
                    log.warn(message, ex)
                    context.result(message)
                }
                context.status(ResponseCode.INTERNAL_SERVER_ERROR)
            }
        }
        return context
    }
}