package net.corda.membership.impl.p2p.handler

import net.corda.messaging.api.records.Record
import net.corda.data.p2p.app.AuthenticatedMessageHeader
import java.nio.ByteBuffer

internal abstract class AuthenticatedMessageHandler : MessageHandler {
    override fun invoke(header: Any, payload: ByteBuffer): Record<*, *> {
        if (header is AuthenticatedMessageHeader) {
            return invokeAuthenticatedMessage(header, payload)
        } else {
            throw UnsupportedOperationException(
                "Handler does not support message type. Only AuthenticatedMessage is allowed."
            )
        }
    }

    abstract fun invokeAuthenticatedMessage(header: AuthenticatedMessageHeader, payload: ByteBuffer): Record<*, *>
}