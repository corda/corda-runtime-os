package net.corda.session.manager.impl.factory

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionAck
import net.corda.data.flow.event.session.SessionClose
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.event.session.SessionError
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.session.SessionState
import net.corda.session.manager.impl.SessionEventProcessor
import net.corda.session.manager.impl.processor.SessionAckProcessor
import net.corda.session.manager.impl.processor.SessionCloseProcessor
import net.corda.session.manager.impl.processor.SessionDataProcessor
import net.corda.session.manager.impl.processor.SessionErrorProcessor
import net.corda.session.manager.impl.processor.SessionInitProcessor
import java.time.Instant

class SessionEventProcessorFactory  {

    /**
     * Get the correct processor for the [sessionEvent] received.
     * [flowKey] is provided for logging purposes
     * [sessionState] is provided for the given [sessionEvent]
     * [instant] is provided for timestamps
     */
    fun create(flowKey: FlowKey, sessionEvent: SessionEvent, sessionState: SessionState?, instant: Instant): SessionEventProcessor {
        return when (val payload = sessionEvent.payload) {
            is SessionInit -> SessionInitProcessor(flowKey, sessionState, sessionEvent, instant)
            is SessionData -> SessionDataProcessor(flowKey, sessionState, sessionEvent, instant)
            is SessionAck -> SessionAckProcessor(flowKey, sessionState, sessionEvent.sessionId, payload.sequenceNum, instant)
            is SessionClose -> SessionCloseProcessor(flowKey, sessionState, sessionEvent, instant)
            is SessionError -> SessionErrorProcessor(flowKey, sessionState, sessionEvent, payload.errorMessage, instant)
            else -> throw NotImplementedError(
                "The session event type '${payload.javaClass.name}' is not supported.")
        }
    }
}
