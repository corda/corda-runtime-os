package net.corda.membership.impl.p2p.handler

import net.corda.messaging.api.records.Record
import java.nio.ByteBuffer

internal abstract class UnauthenticatedMessageHandler : MessageHandler {
    override fun invoke(header: Any, payload: ByteBuffer): Record<*, *>? {
        return invokeUnauthenticatedMessage(payload)
    }

    abstract fun invokeUnauthenticatedMessage(payload: ByteBuffer): Record<*, *>?
}
