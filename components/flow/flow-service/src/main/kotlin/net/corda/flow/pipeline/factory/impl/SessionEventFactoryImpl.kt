package net.corda.flow.pipeline.factory.impl

import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.flow.pipeline.factory.SessionEventFactory
import org.osgi.service.component.annotations.Component
import java.time.Instant

@Component(service = [SessionEventFactory::class])
class SessionEventFactoryImpl :SessionEventFactory{

    override fun create(sessionId:String, nowUtc: Instant, payload: Any): SessionEvent{
        return SessionEvent.newBuilder()
            .setSessionId(sessionId)
            .setMessageDirection(MessageDirection.OUTBOUND)
            .setTimestamp(nowUtc)
            .setSequenceNum(null)
            .setReceivedSequenceNum(0)
            .setOutOfOrderSequenceNums(listOf(0))
            .setPayload(payload)
            .build()
    }
}