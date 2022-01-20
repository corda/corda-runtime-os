package net.corda.session.manager.impl.factory

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionAck
import net.corda.data.flow.event.session.SessionClose
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.event.session.SessionError
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.session.SessionState
import net.corda.session.manager.impl.SessionEventProcessor
import net.corda.session.manager.impl.processor.SessionAckProcessorReceived
import net.corda.session.manager.impl.processor.SessionCloseProcessorReceive
import net.corda.session.manager.impl.processor.SessionCloseProcessorSend
import net.corda.session.manager.impl.processor.SessionDataProcessorReceive
import net.corda.session.manager.impl.processor.SessionDataProcessorSend
import net.corda.session.manager.impl.processor.SessionErrorProcessorReceive
import net.corda.session.manager.impl.processor.SessionErrorProcessorSend
import net.corda.session.manager.impl.processor.SessionInitProcessorReceive
import net.corda.session.manager.impl.processor.SessionInitProcessorSend
import java.time.Instant

class SessionEventProcessorFactory {

    /**
     * Get the correct processor for the [sessionEvent] received.
     * [flowKey] is provided for logging purposes
     * [sessionState] is provided for the given [sessionEvent]
     * [instant] is provided for timestamps
     */
    fun create(flowKey: FlowKey, sessionEvent: SessionEvent, sessionState: SessionState?, instant: Instant): SessionEventProcessor {
        val messageDirection = sessionEvent.messageDirection
        return when (val payload = sessionEvent.payload) {
            is SessionInit -> if (messageDirection == MessageDirection.INBOUND) {
                SessionInitProcessorReceive(flowKey, sessionState, sessionEvent, instant)
            } else {
                SessionInitProcessorSend(flowKey, sessionState, sessionEvent, instant)
            }
            is SessionData -> if (messageDirection == MessageDirection.INBOUND) {
                SessionDataProcessorReceive(flowKey, sessionState, sessionEvent, instant)
            } else {
                SessionDataProcessorSend(flowKey, sessionState, sessionEvent, instant)
            }
            is SessionClose -> if (messageDirection == MessageDirection.INBOUND) {
                SessionCloseProcessorReceive(flowKey, sessionState, sessionEvent, instant)
            } else {
                SessionCloseProcessorSend(flowKey, sessionState, sessionEvent, instant)
            }
            is SessionError -> if (messageDirection == MessageDirection.INBOUND) {
                SessionErrorProcessorReceive(flowKey, sessionState, sessionEvent, payload.errorMessage, instant)
            } else {
                SessionErrorProcessorSend(flowKey, sessionState, sessionEvent, payload.errorMessage, instant)
            }
            is SessionAck -> SessionAckProcessorReceived(flowKey, sessionState, sessionEvent.sessionId, payload.sequenceNum, instant)
            else -> throw NotImplementedError(
                "The session event type '${payload.javaClass.name}' is not supported."
            )
        }
    }
}
