package net.corda.session.manager.impl

import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionAck
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.session.manager.SessionManager
import net.corda.session.manager.impl.factory.SessionEventProcessorFactory
import org.osgi.service.component.annotations.Component
import java.time.Instant

@Component
class SessionManagerImpl : SessionManager {

    private val sessionEventProcessorFactory = SessionEventProcessorFactory()

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
        return when {
            //must be an active session
            (status != SessionStateType.CONFIRMED && status != SessionStateType.CLOSING) -> null
            undeliveredMessages.isEmpty() -> null
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

    override fun getMessagesToSend(sessionState: SessionState): Pair<SessionState, List<SessionEvent>> {
        val messagesToReturn = sessionState.sendEventsState.undeliveredMessages
        //remove SessionAcks
        val messagesWithoutAcks = sessionState.sendEventsState.undeliveredMessages.filter {
            it.payload !is SessionAck
        }
        sessionState.sendEventsState.undeliveredMessages = messagesWithoutAcks
        return Pair(sessionState, messagesToReturn)
    }
}
