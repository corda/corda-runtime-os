package net.corda.membership.impl.p2p.handler

import net.corda.messaging.api.records.Record
import net.corda.data.p2p.app.AuthenticatedMessageHeader
import net.corda.schema.registry.AvroSchemaRegistry
import java.nio.ByteBuffer
import java.util.logging.Logger

abstract class AuthenticatedMessageHandler<T : Any>(
    private val avroSchemaRegistry: AvroSchemaRegistry
) : MessageHandler<T> {
    private companion object {
        private val logger = Logger.getLogger(AuthenticatedMessageHandler::class.java.name)
    }
    override fun invoke(header: Any, payload: ByteBuffer): Record<*, *> {
        when (header) {
            is AuthenticatedMessageHeader -> {
                logger.info(
                    "Invoking p2p handler for ${payloadType.simpleName} with message ID ${header.messageId} and trace ID " +
                        "${header.traceId} from ${header.source} to ${header.destination}."
                )
            }
            else -> {
                throw UnsupportedOperationException(
                    "Handler does not support message type. Only AuthenticatedMessage is allowed."
                )
            }
        }
        val deserialisePayload = avroSchemaRegistry.deserialize(payload, payloadType, null)
        return invokeAuthenticatedMessage(header, deserialisePayload)
    }

    abstract fun invokeAuthenticatedMessage(header: AuthenticatedMessageHeader, payload: T): Record<*, *>
}