package net.corda.session.manager.impl.factory

import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionAck
import net.corda.data.flow.event.session.SessionClose
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.event.session.SessionError
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.session.SessionState
import net.corda.session.manager.SessionManagerException
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
import net.corda.v5.base.util.contextLogger
import java.time.Instant

class SessionEventProcessorFactory {

    private companion object {
        val logger = contextLogger()
    }
    /**
     * Get the correct processor for the [sessionEvent] received.
     * [key] is provided for logging purposes
     * [sessionState] is provided for the given [sessionEvent]
     * [instant] is provided for timestamps
     */
    fun createEventReceivedProcessor(key: Any, sessionEvent: SessionEvent, sessionState: SessionState?, instant: Instant):
            SessionEventProcessor {
        val messageDirection = sessionEvent.messageDirection
        if (messageDirection != MessageDirection.INBOUND) {
            val error = "MessageDirection $messageDirection must be set to ${MessageDirection.INBOUND} for factory method " +
                    "createReceivedEventProcessor()"
            logger.error(error)
            throw SessionManagerException(error)
        }

        return when (val payload = sessionEvent.payload) {
            is SessionInit -> SessionInitProcessorReceive(key, sessionState, sessionEvent, instant)
            is SessionData -> SessionDataProcessorReceive(key, sessionState, sessionEvent, instant)
            is SessionClose -> SessionCloseProcessorReceive(key, sessionState, sessionEvent, instant)
            is SessionError -> SessionErrorProcessorReceive(key, sessionState, sessionEvent, payload.errorMessage, instant)
            is SessionAck -> SessionAckProcessorReceived(key, sessionState, sessionEvent.sessionId, payload.sequenceNum, instant)
            else -> throw NotImplementedError(
                "The session event type '${payload.javaClass.name}' is not supported."
            )
        }
    }

    /**
     * Get the correct processor for the [sessionEvent] to be sent to a counterparty.
     * [key] is provided for logging purposes
     * [sessionState] is provided for the given [sessionEvent]
     * [instant] is provided for timestamps
     */
    fun createEventToSendProcessor(key: Any, sessionEvent: SessionEvent, sessionState: SessionState?, instant: Instant):
            SessionEventProcessor {
        val messageDirection = sessionEvent.messageDirection

        if (messageDirection != MessageDirection.OUTBOUND) {
            val error = "MessageDirection $messageDirection must be set to ${MessageDirection.OUTBOUND} for factory method " +
                    "createEventToSendProcessor()"
            logger.error(error)
            throw SessionManagerException(error)
        }
        return when (val payload = sessionEvent.payload) {
            is SessionInit -> SessionInitProcessorSend(key, sessionState, sessionEvent, instant)
            is SessionData -> SessionDataProcessorSend(key, sessionState, sessionEvent, instant)
            is SessionClose -> SessionCloseProcessorSend(key, sessionState, sessionEvent, instant)
            is SessionError -> SessionErrorProcessorSend(key, sessionState, sessionEvent, payload.errorMessage, instant)
            else -> throw NotImplementedError(
                "The session event type '${payload.javaClass.name}' is not supported."
            )
        }
    }
}
