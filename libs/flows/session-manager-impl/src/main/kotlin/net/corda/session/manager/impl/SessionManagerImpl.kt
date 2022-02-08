package net.corda.session.manager.impl

import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionAck
import net.corda.data.flow.event.session.SessionClose
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.libs.configuration.SmartConfig
import net.corda.schema.configuration.FlowConfig.SESSION_MESSAGE_RESEND_WINDOW
import net.corda.session.manager.SessionManager
import net.corda.session.manager.impl.factory.SessionEventProcessorFactory
import org.osgi.service.component.annotations.Component
import java.time.Instant

@Component
class SessionManagerImpl : SessionManager {

    private companion object {
        val sessionEventProcessorFactory = SessionEventProcessorFactory()
    }

    override fun processMessageReceived(key: Any, sessionState: SessionState?, event: SessionEvent, instant: Instant):
            SessionState {
        return sessionEventProcessorFactory.createEventReceivedProcessor(key, event, sessionState, instant).execute()
    }

    override fun processMessageToSend(key: Any, sessionState: SessionState?, event: SessionEvent, instant: Instant):
            SessionState {
        return sessionEventProcessorFactory.createEventToSendProcessor(key, event, sessionState, instant).execute()
    }

    override fun getNextReceivedEvent(sessionState: SessionState): SessionEvent? {
        val receivedEvents = sessionState.receivedEventsState ?: return null
        val undeliveredMessages = receivedEvents.undeliveredMessages
        val status = sessionState.status
        val incorrectSessionState = status == SessionStateType.CREATED || status == SessionStateType.ERROR
        return when {
            //must be an active session
            undeliveredMessages.isEmpty() -> null
            //don't allow data messages to be consumed when session is not fully established or if there is an error
            incorrectSessionState -> null
            //only allow client to see a close message after the session is closed on both sides
            status != SessionStateType.CLOSED && undeliveredMessages.first().payload is SessionClose -> null
            //return the next valid message
            undeliveredMessages.first().sequenceNum <= receivedEvents.lastProcessedSequenceNum -> undeliveredMessages.first()
            else -> null
        }
    }

    override fun acknowledgeReceivedEvent(sessionState: SessionState, seqNum: Int): SessionState {
        return sessionState.apply {
            val receivedEvent = receivedEventsState.undeliveredMessages.find {
                it.sequenceNum == seqNum
            }
            receivedEventsState.undeliveredMessages = receivedEventsState.undeliveredMessages.minus(receivedEvent)
        }
    }

    override fun getMessagesToSend(sessionState: SessionState, instant: Instant, config: SmartConfig): Pair<SessionState,
            List<SessionEvent>> {
        val instantInMillis = instant.toEpochMilli()
        val messagesToReturn = sessionState.sendEventsState.undeliveredMessages.filter { it.timestamp <= instantInMillis || it
            .payload is SessionAck}

        //remove SessionAcks and time increased
        sessionState.sendEventsState.undeliveredMessages = sessionState.sendEventsState.undeliveredMessages.filter {
            it.payload !is SessionAck
        }.map {
            if (it.timestamp <= instantInMillis) {
                it.timestamp = instantInMillis + config.getLong(SESSION_MESSAGE_RESEND_WINDOW)
            }
            it
        }

        return Pair(sessionState, messagesToReturn)
    }
}
