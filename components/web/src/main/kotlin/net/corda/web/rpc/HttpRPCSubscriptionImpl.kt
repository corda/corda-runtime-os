package net.corda.web.rpc

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.messaging.api.processor.HttpRPCProcessor
import net.corda.messaging.api.subscription.HttpRPCSubscription
import net.corda.web.server.HTTPMethod
import net.corda.web.server.WebServer
import org.eclipse.jetty.http.HttpStatus
import org.slf4j.LoggerFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference


/**
 * An implementation of the HttpRPCSubscription interface.
 *
 * @param cordaAvroSerializationFactory The CordaAvroSerializationFactory service used for Avro serialization and deserialization.
 * @param javalinServer The JavalinServer service used for server operations. This must be previously initialized and started
 */
@Component(service = [HttpRPCSubscription::class])
class HttpRPCSubscriptionImpl @Activate constructor(
    @Reference(service = CordaAvroSerializationFactory::class)
    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    @Reference(service = WebServer::class)
    private val javalinServer: WebServer
) : HttpRPCSubscription {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    /**
     * Registers an endpoint with the provided handler function and request payload class.
     *
     * @param endpoint The endpoint URL path.
     * @param handler The handler function to process the request payload.
     * @param clazz The class of the request payload.
     */
    override fun <REQ : Any, RESP : Any> registerEndpoint(endpoint: String, handler: HttpRPCProcessor<REQ, RESP>, clazz: Class<REQ>) {
        val server = javalinServer

        val avroDeserializer = cordaAvroSerializationFactory.createAvroDeserializer({
            log.error("Failed to deserialize payload for request")
        }, clazz)

        val avroSerializer = cordaAvroSerializationFactory.createAvroSerializer<RESP> {
            log.error("Failed to serialize payload for response")
        }

        server.registerHandler(HTTPMethod.POST, endpoint) { context ->

            val payload = avroDeserializer.deserialize(context.bodyAsBytes())

            if (payload != null) {
                val serializedResponse = avroSerializer.serialize(handler.handle(payload, context))
                if (serializedResponse != null) {
                    context.result(serializedResponse)
                    return@registerHandler context
                } else {
                    log.error("Response Payload was Null")
                    context.result("Response Payload was Null")
                    context.status(HttpStatus.UNPROCESSABLE_ENTITY_422)
                    return@registerHandler context
                }
            } else {
                log.error("Request Payload was Null")
                context.result("Request Payload was Null")
                context.status(HttpStatus.UNPROCESSABLE_ENTITY_422)
                return@registerHandler context
            }
        }
    }
}