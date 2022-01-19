package net.corda.session.manager.impl

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.state.session.SessionState
import net.corda.messaging.api.records.Record
import net.corda.session.manager.SessionEventResult
import net.corda.session.manager.SessionManager
import net.corda.session.manager.impl.factory.SessionEventProcessorFactory
import net.corda.session.manager.impl.processor.helper.generateErrorEvent
import org.osgi.service.component.annotations.Component
import java.time.Instant

@Component
class SessionManagerImpl : SessionManager {

    private val sessionEventProcessorFactory = SessionEventProcessorFactory()

    override fun processMessage(flowKey: FlowKey, sessionState: SessionState?, event: SessionEvent, instant: Instant): SessionEventResult {
        return sessionEventProcessorFactory.create(flowKey, event, sessionState, instant).execute()
    }

    override fun getNextReceivedEvent(sessionState: SessionState?): SessionEvent? {
        val receivedEvents = sessionState?.receivedEventsState ?: return null
        val undeliveredMessages = receivedEvents.undeliveredMessages
        return when {
            undeliveredMessages.isEmpty() -> null
            undeliveredMessages.first().sequenceNum <= receivedEvents.lastProcessedSequenceNum -> undeliveredMessages.first()
            else -> null
        }
    }

    override fun acknowledgeReceivedEvent(sessionState: SessionState, seqNum: Int): SessionState {
        val undeliveredEventsReceived = sessionState.receivedEventsState.undeliveredMessages.toMutableList()
        undeliveredEventsReceived.removeIf { it.sequenceNum == seqNum }
        sessionState.receivedEventsState.undeliveredMessages = undeliveredEventsReceived
        return sessionState
    }

    override fun generateSessionErrorEvent(sessionId: String, errorMessage: String, errorType: String, instant: Instant):
            Record<String, FlowMapperEvent> {
        return generateErrorEvent(sessionId, errorMessage, errorType, instant)
    }
}
