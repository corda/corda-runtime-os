package net.corda.membership.impl.p2p.handler

import net.corda.messaging.api.records.Record
import java.nio.ByteBuffer

internal interface MessageHandler {
    fun invoke(header: Any, payload: ByteBuffer): Record<*, *>
}