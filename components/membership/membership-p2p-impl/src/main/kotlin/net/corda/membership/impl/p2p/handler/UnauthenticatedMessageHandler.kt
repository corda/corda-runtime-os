package net.corda.membership.impl.p2p.handler

import net.corda.messaging.api.records.Record
import net.corda.p2p.app.UnauthenticatedMessageHeader
import java.nio.ByteBuffer

abstract class UnauthenticatedMessageHandler : MessageHandler {
    override fun invoke(header: Any, payload: ByteBuffer): Record<*, *> {
        if (header is UnauthenticatedMessageHeader) {
            return invokeUnautheticatedMessage(header, payload)
        } else {
            throw UnsupportedOperationException(
                "Handler does not support message type. Only UnauthenticatedMessage is allowed."
            )
        }
    }

    abstract fun invokeUnautheticatedMessage(header: UnauthenticatedMessageHeader, payload: ByteBuffer): Record<*, *>
}