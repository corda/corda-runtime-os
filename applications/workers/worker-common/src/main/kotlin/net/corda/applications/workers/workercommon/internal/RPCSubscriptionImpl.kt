package net.corda.applications.workers.workercommon.internal

import net.corda.applications.workers.workercommon.RPCSubscription
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.applications.workers.workercommon.web.JavalinServer
import net.corda.applications.workers.workercommon.web.WorkerWebServer
import org.eclipse.jetty.http.HttpStatus
import org.slf4j.LoggerFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference


/**
 * An implementation of the RPCSubscription interface.
 *
 * @param cordaAvroSerializationFactory The CordaAvroSerializationFactory service used for Avro serialization and deserialization.
 * @param javalinServer The JavalinServer service used for server operations. This must be previously initialized and started
 */
@Component(service = [RPCSubscription::class])
class RPCSubscriptionImpl @Activate constructor(
    @Reference(service = CordaAvroSerializationFactory::class)
    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    @Reference(service = JavalinServer::class)
    private val javalinServer: WorkerWebServer
) : RPCSubscription {

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
    override fun <REQ : Any, RESP : Any> registerEndpoint(endpoint: String, handler: (REQ) -> RESP, clazz: Class<REQ>) {
        val server = javalinServer

        val avroDeserializer = cordaAvroSerializationFactory.createAvroDeserializer({
            log.error("Failed to deserialize payload for request")
        }, clazz)

        val avroSerializer = cordaAvroSerializationFactory.createAvroSerializer<RESP> {
            log.error("Failed to serialize payload for response")
        }

        server.post(endpoint) { context ->

            val payload = avroDeserializer.deserialize(context.bodyAsBytes())

            if (payload != null) {
                val serializedResponse = avroSerializer.serialize(handler(payload))
                if (serializedResponse != null) {
                    context.result(serializedResponse)
                    return@post context
                } else {
                    log.error("Response Payload was Null")
                    context.result("Response Payload was Null")
                    context.status(HttpStatus.UNPROCESSABLE_ENTITY_422)
                    return@post context
                }
            } else {
                log.error("Request Payload was Null")
                context.result("Request Payload was Null")
                context.status(HttpStatus.UNPROCESSABLE_ENTITY_422)
                return@post context
            }
        }
    }
}