package net.corda.membership.impl.p2p.handler

import net.corda.messaging.api.records.Record
import net.corda.data.p2p.app.UnauthenticatedMessageHeader
import java.nio.ByteBuffer

internal abstract class UnauthenticatedMessageHandler : MessageHandler {
    override fun invoke(header: Any, payload: ByteBuffer): Record<*, *>? {
        if (header is UnauthenticatedMessageHeader) {
            return invokeUnauthenticatedMessage(header, payload)
        } else {
            throw UnsupportedOperationException(
                "Handler does not support message type. Only UnauthenticatedMessage is allowed."
            )
        }
    }

    abstract fun invokeUnauthenticatedMessage(header: UnauthenticatedMessageHeader, payload: ByteBuffer): Record<*, *>?
}