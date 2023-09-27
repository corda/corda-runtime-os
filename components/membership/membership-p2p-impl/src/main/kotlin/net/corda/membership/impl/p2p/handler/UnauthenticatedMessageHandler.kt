package net.corda.membership.impl.p2p.handler

import net.corda.data.p2p.app.InboundUnauthenticatedMessageHeader
import net.corda.data.p2p.app.OutboundUnauthenticatedMessageHeader
import net.corda.messaging.api.records.Record
import net.corda.schema.registry.AvroSchemaRegistry
import java.nio.ByteBuffer
import java.util.logging.Logger

internal abstract class UnauthenticatedMessageHandler<T : Any>(
    private val avroSchemaRegistry: AvroSchemaRegistry
) : MessageHandler<T> {
    private companion object {
        private val logger = Logger.getLogger(UnauthenticatedMessageHandler::class.java.name)
    }

    override fun invoke(header: Any, payload: ByteBuffer): Record<*, *>? {
        when (header) {
            is InboundUnauthenticatedMessageHeader -> {
                logger.info("Invoking p2p handler for ${payloadType.simpleName} with message ID ${header.messageId}.")
            }
            is OutboundUnauthenticatedMessageHeader -> {
                logger.info("Invoking p2p handler for ${payloadType.simpleName} with message ID ${header.messageId} " +
                        "from ${header.source} to ${header.destination}.")
            }
            else -> {
                throw UnsupportedOperationException(
                    "Handler does not support message type. Only InboundUnauthenticatedMessage and " +
                            "OutboundUnauthenticatedMessage are allowed."
                )
            }
        }
        val deserialisePayload = avroSchemaRegistry.deserialize(payload, payloadType, null)
        return invokeUnauthenticatedMessage(deserialisePayload)
    }

    abstract fun invokeUnauthenticatedMessage(payload: T): Record<*, *>?
}