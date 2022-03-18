package net.corda.flow.pipeline.factory

import net.corda.data.flow.event.SessionEvent
import java.time.Instant

interface SessionEventFactory {
    fun create(sessionId:String, nowUtc: Instant, payload: Any): SessionEvent
}