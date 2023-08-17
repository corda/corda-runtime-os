package net.corda.rpc.server

import net.corda.avro.serialization.CordaAvroSerializationFactory
import io.javalin.http.Handler
import net.corda.applications.workers.workercommon.JavalinServer
import org.eclipse.jetty.http.HttpStatus
import org.slf4j.LoggerFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference


/**
 * An implementation of the RPCServer interface.
 *
 * @param cordaAvroSerializationFactory The CordaAvroSerializationFactory service used for Avro serialization and deserialization.
 * @param javalinServer The JavalinServer service used for server operations. This must be previously initialized and started
 */
@Component(service = [RPCServer::class])
class RPCServerImpl @Activate constructor(
    @Reference(service = CordaAvroSerializationFactory::class)
    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    @Reference(service = JavalinServer::class)
    private val javalinServer: JavalinServer
) : RPCServer {

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
        val server = javalinServer.getServer()
        if (server != null) {
            server.post(endpoint, Handler { context ->

                val avroDeserializer = cordaAvroSerializationFactory.createAvroDeserializer({
                    log.error("Failed to deserialize payload for request")
                    context.result("Failed to deserialize request payload")
                    context.status(HttpStatus.INTERNAL_SERVER_ERROR_500)
                }, clazz)

                val avroSerializer = cordaAvroSerializationFactory.createAvroSerializer<RESP> {
                    log.error("Failed to serialize payload for response")
                    context.result("Failed to serialize response payload")
                    context.status(HttpStatus.INTERNAL_SERVER_ERROR_500)
                }

                val payload = avroDeserializer.deserialize(context.bodyAsBytes())

                if (payload != null) {
                    val serializedResponse = avroSerializer.serialize(handler(payload))
                    if (serializedResponse != null) {
                        context.result(serializedResponse)
                    } else {
                        log.error("Response Payload was Null")
                        context.result("Response Payload was Null")
                        context.status(HttpStatus.UNPROCESSABLE_ENTITY_422)
                    }
                } else {
                    log.error("Request Payload was Null")
                    context.result("Request Payload was Null")
                    context.status(HttpStatus.UNPROCESSABLE_ENTITY_422)
                }
            })
        } else {
            throw Exception("The Javalin Server must be initialized before routes are added")
        }
    }
}