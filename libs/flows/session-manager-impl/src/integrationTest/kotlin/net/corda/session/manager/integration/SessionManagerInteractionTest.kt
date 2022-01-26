package net.corda.session.manager.integration

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionClose
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.session.SessionProcessState
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.data.identity.HoldingIdentity
import net.corda.session.manager.impl.SessionManagerImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class SessionManagerInteractionTest {

    private val sessionManager = SessionManagerImpl()
    private val aliceIdentity = HoldingIdentity("Alice", "group1")
    private val bobIdentity = HoldingIdentity("Bob", "group1")
    private val aliceFlowKey = FlowKey("Flow1", aliceIdentity)
    private val bobFlowKey = FlowKey("Flow2", bobIdentity)
    private val flowName = "FlowName"
    private val cpiId = "CPI1"
    private val instant = Instant.now()
    private val sessionId = "sessionId"
    private val sessionIdInitiated = "$sessionId-INITIATED"

    @Test
    fun testFullSessionSendAndReceive() {
        //=============== Init =======================

        //send INIT
        val aliceSessionInit = SessionInit(flowName, cpiId, aliceFlowKey, bobIdentity, aliceIdentity, null)
        val aliceSessionEvent1 = SessionEvent(MessageDirection.OUTBOUND, instant.toEpochMilli(), null, null, aliceSessionInit)
        var aliceSessionState = sessionManager.processMessageToSend(aliceFlowKey, null, aliceSessionEvent1, instant)
        assertThat(aliceSessionState.status).isEqualTo(SessionStateType.CREATED)

        //FlowMapper Changes
        val bobSessionInitReceived = sessionManager.getMessagesToSend(aliceSessionState).second.first()
        bobSessionInitReceived.messageDirection = MessageDirection.INBOUND

        //Receive Init
        var bobSessionState = sessionManager.processMessageReceived(bobFlowKey, null, bobSessionInitReceived, instant)
        assertThat(bobSessionState.status).isEqualTo(SessionStateType.CONFIRMED)
        assertThat(bobSessionState).isNotNull

        //FlowMapper Changes
        val aliceSessionEventAck1 = sessionManager.getMessagesToSend(bobSessionState).second.first()
        aliceSessionEventAck1.messageDirection = MessageDirection.INBOUND

        //Receive Ack
        aliceSessionState = sessionManager.processMessageReceived(aliceFlowKey, aliceSessionState, aliceSessionEventAck1, instant)
        assertThat(aliceSessionState).isNotNull

        //bob processes init
        var bobNextEvent = sessionManager.getNextReceivedEvent(bobSessionState)
        assertThat(bobNextEvent?.sequenceNum).isEqualTo(1)
        assertThat(bobSessionState.receivedEventsState?.undeliveredMessages?.size).isEqualTo(1)
        bobSessionState = sessionManager.acknowledgeReceivedEvent(bobSessionState, bobNextEvent!!.sequenceNum)
        assertThat(bobSessionState.receivedEventsState.undeliveredMessages).isEmpty()

        //=============== Data =======================
        //send data
        val aliceSessionData = SessionData(null)
        val aliceSessionEvent2 = SessionEvent(MessageDirection.OUTBOUND, instant.toEpochMilli(), sessionIdInitiated, null, aliceSessionData)
        aliceSessionState = sessionManager.processMessageToSend(aliceFlowKey, aliceSessionState, aliceSessionEvent2, instant)
        assertThat(aliceSessionState).isNotNull

        //mapper changes
        val bobSessionDataReceived = aliceSessionState.sentEventsState?.undeliveredMessages?.last()!!
        bobSessionDataReceived.messageDirection = MessageDirection.INBOUND

        //data received
        bobSessionState = sessionManager.processMessageReceived(bobFlowKey, bobSessionState, bobSessionDataReceived, instant)
        assertThat(bobSessionState).isNotNull

        //FlowMapper Changes
        val aliceSessionEventAck2 = sessionManager.getMessagesToSend(bobSessionState).second.first()
        aliceSessionEventAck2.messageDirection = MessageDirection.INBOUND

        //Receive Ack
        aliceSessionState = sessionManager.processMessageReceived(aliceFlowKey, aliceSessionState, aliceSessionEventAck2, instant)
        assertThat(aliceSessionState).isNotNull

        val bobSessionEventReceived = sessionManager.getNextReceivedEvent(bobSessionState)
        assertThat(bobSessionEventReceived?.sequenceNum).isEqualTo(2)
        assertThat(bobSessionState.receivedEventsState?.undeliveredMessages?.size).isEqualTo(1)

        //bob processes data
        bobNextEvent = sessionManager.getNextReceivedEvent(bobSessionState)
        bobSessionState = sessionManager.acknowledgeReceivedEvent(bobSessionState, bobNextEvent!!.sequenceNum)
        assertThat(bobSessionState.receivedEventsState.undeliveredMessages).isEmpty()

        //=============== ALice  Close =======================
        //Alice send close
        val aliceSessionClose = SessionClose()
        val aliceSessionEvent3= SessionEvent(MessageDirection.OUTBOUND, instant.toEpochMilli(), sessionIdInitiated, null, aliceSessionClose)
        aliceSessionState = sessionManager.processMessageToSend(aliceFlowKey, aliceSessionState, aliceSessionEvent3, instant)
        assertThat(aliceSessionState.status).isEqualTo(SessionStateType.CLOSING)

        //mapper changes
        val bobSessionCloseReceived = aliceSessionState.sentEventsState?.undeliveredMessages?.last()!!
        bobSessionCloseReceived.messageDirection = MessageDirection.INBOUND

        //Bob receive close
        bobSessionState = sessionManager.processMessageReceived(bobFlowKey, bobSessionState, bobSessionCloseReceived, instant)
        assertThat(bobSessionState.status).isEqualTo(SessionStateType.CLOSING)

        //FlowMapper Changes
        val aliceSessionEventAck3 = sessionManager.getMessagesToSend(bobSessionState).second.first()
        aliceSessionEventAck3.messageDirection = MessageDirection.INBOUND

        //Alice receive Ack
        aliceSessionState = sessionManager.processMessageReceived(aliceFlowKey, aliceSessionState, aliceSessionEventAck3, instant)
        assertThat(aliceSessionState.status).isEqualTo(SessionStateType.CLOSING)
        assertThat(aliceSessionState).isNotNull

        //=============== Bob  Close =======================
        //bob send close
        val bobSessionClose = SessionClose()
        val bobSessionEvent = SessionEvent(MessageDirection.OUTBOUND, instant.toEpochMilli(), sessionIdInitiated, null, bobSessionClose)
        bobSessionState = sessionManager.processMessageToSend(bobFlowKey, bobSessionState, bobSessionEvent, instant)
        assertThat(bobSessionState.status).isEqualTo(SessionStateType.WAIT_FOR_FINAL_ACK)
        assertThat(bobSessionState).isNotNull

        //mapper changes
        val aliceSessionCloseReceived = bobSessionState.sentEventsState?.undeliveredMessages?.last()!!
        aliceSessionCloseReceived.messageDirection = MessageDirection.INBOUND

        //alice receive close
        aliceSessionState = sessionManager.processMessageReceived(aliceFlowKey, aliceSessionState, aliceSessionCloseReceived, instant)
        assertThat(aliceSessionState.status).isEqualTo(SessionStateType.CLOSED)

        //FlowMapper Changes
        val bobSessionEventAck = sessionManager.getMessagesToSend(aliceSessionState).second.first()
        bobSessionEventAck.messageDirection = MessageDirection.INBOUND

        //bob Receive Ack
        bobSessionState = sessionManager.processMessageReceived(bobFlowKey, bobSessionState, bobSessionEventAck, instant)
        assertThat(bobSessionState.status).isEqualTo(SessionStateType.CLOSED)
    }

    @Test
    fun testSendOutOfOrderData() {
        var aliceSessionState: SessionState? = SessionState(sessionId, 1, HoldingIdentity("Bob", "1"), true,
            SessionProcessState(0, mutableListOf()),
            SessionProcessState(1, mutableListOf()),
            SessionStateType.CONFIRMED)
        val aliceSessionData = SessionData(null)

        //send 1st data
        val aliceSessionEvent1 = SessionEvent(MessageDirection.OUTBOUND, instant.toEpochMilli(), sessionId, null, aliceSessionData)
        aliceSessionState = sessionManager.processMessageToSend(aliceFlowKey, aliceSessionState, aliceSessionEvent1, instant)
        var sentEventsState = aliceSessionState.sentEventsState
        assertThat(sentEventsState?.lastProcessedSequenceNum).isEqualTo(2)
        assertThat(sentEventsState?.undeliveredMessages?.size).isEqualTo(1)

        //FlowMapper Changes
        val bobSessionEvent2 = aliceSessionState.sentEventsState?.undeliveredMessages?.last()!!
        bobSessionEvent2.messageDirection = MessageDirection.INBOUND

        //send 2nd data
        val aliceSessionEvent2 = SessionEvent(MessageDirection.OUTBOUND, instant.toEpochMilli(), sessionId, null, aliceSessionData)
        aliceSessionState = sessionManager.processMessageToSend(aliceFlowKey, aliceSessionState, aliceSessionEvent2, instant)
        sentEventsState = aliceSessionState.sentEventsState
        assertThat(aliceSessionState).isNotNull
        assertThat(sentEventsState?.lastProcessedSequenceNum).isEqualTo(3)
        assertThat(sentEventsState?.undeliveredMessages?.size).isEqualTo(2)

        //FlowMapper Changes
        val bobSessionEvent1 = aliceSessionState.sentEventsState?.undeliveredMessages?.last()!!
        bobSessionEvent1.messageDirection = MessageDirection.INBOUND

        var bobSessionState: SessionState? = SessionState(sessionIdInitiated, 1, HoldingIdentity("Alice", "1"), false,
            SessionProcessState(1, listOf()),
            SessionProcessState(0, listOf()),
            SessionStateType.CONFIRMED)

        //receive out of order data
        bobSessionState = sessionManager.processMessageReceived(bobFlowKey, bobSessionState, bobSessionEvent1, instant)
        assertThat(bobSessionState.receivedEventsState?.lastProcessedSequenceNum).isEqualTo(1)
        assertThat(bobSessionState.status).isEqualTo(SessionStateType.CONFIRMED)

        //receive out of order data
        bobSessionState = sessionManager.processMessageReceived(bobFlowKey, bobSessionState, bobSessionEvent2, instant)
        assertThat(bobSessionState.receivedEventsState?.lastProcessedSequenceNum).isEqualTo(3)
        assertThat(bobSessionState.status).isEqualTo(SessionStateType.CONFIRMED)

        var bobNextEvent = sessionManager.getNextReceivedEvent(bobSessionState)
        assertThat(bobNextEvent?.sequenceNum).isEqualTo(2)
        assertThat(bobSessionState.receivedEventsState?.lastProcessedSequenceNum).isEqualTo(3)
        bobSessionState = sessionManager.acknowledgeReceivedEvent(bobSessionState, bobNextEvent!!.sequenceNum)
        assertThat(bobSessionState.receivedEventsState.undeliveredMessages.size).isEqualTo(1)

        bobNextEvent = sessionManager.getNextReceivedEvent(bobSessionState)
        assertThat(bobNextEvent?.sequenceNum).isEqualTo(3)
        bobSessionState = sessionManager.acknowledgeReceivedEvent(bobSessionState, bobNextEvent!!.sequenceNum)
        assertThat(bobSessionState.receivedEventsState.undeliveredMessages).isEmpty()
        assertThat(bobSessionState.receivedEventsState.lastProcessedSequenceNum).isEqualTo(3)
    }
}
