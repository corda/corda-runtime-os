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
        val aliceResultInit = sessionManager.processMessageToSend(aliceFlowKey, null, aliceSessionEvent1, instant)
        var aliceSessionState = aliceResultInit.sessionState
        assertThat(aliceSessionState).isNotNull
        assertThat(aliceSessionState?.status).isEqualTo(SessionStateType.CREATED)
        assertThat(aliceResultInit.outputSessionEvents).isNotNull

        //FlowMapper Changes
        val bobSessionInitReceived = aliceResultInit.outputSessionEvents?.first()!!
        bobSessionInitReceived.messageDirection = MessageDirection.INBOUND
        val bobSessionInit = bobSessionInitReceived.payload as SessionInit
        bobSessionInit.flowKey = null

        //Receive Init
        val bobResultInitResult = sessionManager.processMessageReceived(bobFlowKey, null, bobSessionInitReceived, instant)
        var bobSessionState = bobResultInitResult.sessionState
        assertThat(bobSessionState?.status).isEqualTo(SessionStateType.CONFIRMED)
        assertThat(bobSessionState).isNotNull
        assertThat(bobResultInitResult.outputSessionEvents).isNotNull

        //FlowMapper Changes
        val aliceSessionEventAck1 = bobResultInitResult.outputSessionEvents?.first()!!
        aliceSessionEventAck1.messageDirection = MessageDirection.INBOUND

        //Receive Ack
        val aliceResultAck1Result = sessionManager.processMessageReceived(aliceFlowKey, aliceSessionState, aliceSessionEventAck1, instant)
        aliceSessionState = aliceResultAck1Result.sessionState
        assertThat(aliceSessionState).isNotNull
        assertThat(aliceResultAck1Result.outputSessionEvents).isNull()

        //bob processes init
        var bobNextEvent = sessionManager.getNextReceivedEvent(bobSessionState!!)
        assertThat(bobNextEvent?.sequenceNum).isEqualTo(1)
        assertThat(bobSessionState.receivedEventsState?.undeliveredMessages?.size).isEqualTo(1)
        bobSessionState = sessionManager.acknowledgeReceivedEvent(bobSessionState, bobNextEvent!!.sequenceNum)
        assertThat(bobSessionState.receivedEventsState.undeliveredMessages).isEmpty()

        //=============== Data =======================
        //send data
        val aliceSessionData = SessionData(null)
        val aliceSessionEvent2 = SessionEvent(MessageDirection.OUTBOUND, instant.toEpochMilli(), sessionIdInitiated, null, aliceSessionData)
        val aliceResultDataSentResult = sessionManager.processMessageToSend(aliceFlowKey, aliceSessionState, aliceSessionEvent2, instant)
        aliceSessionState = aliceResultDataSentResult.sessionState
        assertThat(aliceSessionState).isNotNull
        assertThat(aliceResultDataSentResult.outputSessionEvents).isNotNull

        //mapper changes
        val bobSessionDataReceived = aliceResultDataSentResult.outputSessionEvents?.first()!!
        bobSessionDataReceived.messageDirection = MessageDirection.INBOUND

        //data received
        val bobResultDataReceivedResult = sessionManager.processMessageReceived(bobFlowKey, bobSessionState, bobSessionDataReceived, instant)
        bobSessionState = bobResultDataReceivedResult.sessionState
        assertThat(bobSessionState).isNotNull
        assertThat(bobResultDataReceivedResult.outputSessionEvents).isNotNull

        //FlowMapper Changes
        val aliceSessionEventAck2 = bobResultDataReceivedResult.outputSessionEvents?.first()!!
        aliceSessionEventAck2.messageDirection = MessageDirection.INBOUND

        //Receive Ack
        val aliceResultAck2Result = sessionManager.processMessageReceived(aliceFlowKey, aliceSessionState, aliceSessionEventAck2, instant)
        aliceSessionState = aliceResultAck2Result.sessionState
        assertThat(aliceSessionState).isNotNull
        assertThat(aliceResultAck2Result.outputSessionEvents).isNull()

        val bobSessionEventReceived = sessionManager.getNextReceivedEvent(bobSessionState!!)
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
        val aliceResultCloseSent = sessionManager.processMessageToSend(aliceFlowKey, aliceSessionState, aliceSessionEvent3, instant)
        aliceSessionState = aliceResultCloseSent.sessionState
        assertThat(aliceSessionState?.status).isEqualTo(SessionStateType.CLOSING)
        assertThat(aliceResultCloseSent.outputSessionEvents).isNotNull

        //mapper changes
        val bobSessionCloseReceived = aliceResultCloseSent.outputSessionEvents?.first()!!
        bobSessionCloseReceived.messageDirection = MessageDirection.INBOUND

        //Bob receive close
        val bobResultCloseReceivedResult = sessionManager.processMessageReceived(bobFlowKey, bobSessionState, bobSessionCloseReceived, instant)
        bobSessionState = bobResultCloseReceivedResult.sessionState
        assertThat(bobSessionState?.status).isEqualTo(SessionStateType.CLOSING)
        assertThat(bobResultCloseReceivedResult.outputSessionEvents).isNotNull

        //FlowMapper Changes
        val aliceSessionEventAck3 = bobResultCloseReceivedResult.outputSessionEvents?.first()!!
        aliceSessionEventAck3.messageDirection = MessageDirection.INBOUND

        //Alice receive Ack
        val aliceResultAck3Result = sessionManager.processMessageReceived(aliceFlowKey, aliceSessionState, aliceSessionEventAck3, instant)
        aliceSessionState = aliceResultAck3Result.sessionState
        assertThat(aliceSessionState?.status).isEqualTo(SessionStateType.CLOSING)
        assertThat(aliceSessionState).isNotNull
        assertThat(aliceResultAck3Result.outputSessionEvents).isNull()

        //=============== Bob  Close =======================
        //bob send close
        val bobSessionClose = SessionClose()
        val bobSessionEvent = SessionEvent(MessageDirection.OUTBOUND, instant.toEpochMilli(), sessionIdInitiated, null, bobSessionClose)
        val bobResultCloseSentResult = sessionManager.processMessageToSend(bobFlowKey, bobSessionState, bobSessionEvent, instant)
        bobSessionState = bobResultCloseSentResult.sessionState
        assertThat(bobSessionState?.status).isEqualTo(SessionStateType.WAIT_FOR_FINAL_ACK)
        assertThat(bobSessionState).isNotNull
        assertThat(bobResultCloseSentResult.outputSessionEvents).isNotNull

        //mapper changes
        val aliceSessionCloseReceived = bobResultCloseSentResult.outputSessionEvents?.first()!!
        aliceSessionCloseReceived.messageDirection = MessageDirection.INBOUND

        //alice receive close
        val aliceResultCloseReceivedResult = sessionManager.processMessageReceived(aliceFlowKey, aliceSessionState, aliceSessionCloseReceived, instant)
        aliceSessionState = aliceResultCloseReceivedResult.sessionState
        assertThat(aliceSessionState).isNotNull
        assertThat(aliceSessionState?.status).isEqualTo(SessionStateType.CLOSED)
        assertThat(aliceResultCloseReceivedResult.outputSessionEvents).isNotNull

        //FlowMapper Changes
        val bobSessionEventAck = aliceResultCloseReceivedResult.outputSessionEvents?.first()!!
        bobSessionEventAck.messageDirection = MessageDirection.INBOUND

        //bob Receive Ack
        val bobResultAck3Result = sessionManager.processMessageReceived(bobFlowKey, bobSessionState, bobSessionEventAck, instant)
        bobSessionState = bobResultAck3Result.sessionState
        assertThat(bobSessionState).isNotNull
        assertThat(bobSessionState?.status).isEqualTo(SessionStateType.CLOSED)
        assertThat(bobResultAck3Result.outputSessionEvents).isNull()
    }

    @Test
    fun testSendOutOfOrderData() {
        var aliceSessionState: SessionState? = SessionState(sessionId, 1, HoldingIdentity("Bob", "1"), true,
            SessionProcessState(0, listOf()),
            SessionProcessState(1, listOf()),
            SessionStateType.CONFIRMED)
        val aliceSessionData = SessionData(null)

        //send 1st data
        val aliceSessionEvent1 = SessionEvent(MessageDirection.OUTBOUND, instant.toEpochMilli(), sessionId, null, aliceSessionData)
        val aliceResult1 = sessionManager.processMessageToSend(aliceFlowKey, aliceSessionState, aliceSessionEvent1, instant)
        aliceSessionState = aliceResult1.sessionState
        var sentEventsState = aliceSessionState?.sentEventsState
        assertThat(sentEventsState?.lastProcessedSequenceNum).isEqualTo(2)
        assertThat(sentEventsState?.undeliveredMessages?.size).isEqualTo(1)

        //send 2nd data
        val aliceSessionEvent2 = SessionEvent(MessageDirection.OUTBOUND, instant.toEpochMilli(), sessionId, null, aliceSessionData)
        val aliceResult2 = sessionManager.processMessageToSend(aliceFlowKey, aliceSessionState, aliceSessionEvent2, instant)
        aliceSessionState = aliceResult2.sessionState
        sentEventsState = aliceSessionState?.sentEventsState
        assertThat(aliceSessionState).isNotNull
        assertThat(sentEventsState?.lastProcessedSequenceNum).isEqualTo(3)
        assertThat(sentEventsState?.undeliveredMessages?.size).isEqualTo(2)

        //FlowMapper Changes
        val bobSessionEvent1 = aliceResult2.outputSessionEvents?.first()!!
        bobSessionEvent1.messageDirection = MessageDirection.INBOUND
        val bobSessionEvent2 = aliceResult1.outputSessionEvents?.first()!!
        bobSessionEvent2.messageDirection = MessageDirection.INBOUND

        var bobSessionState: SessionState? = SessionState(sessionIdInitiated, 1, HoldingIdentity("Alice", "1"), false,
            SessionProcessState(1, listOf()),
            SessionProcessState(0, listOf()),
            SessionStateType.CONFIRMED)

        //receive out of order data
        val bobResult1 = sessionManager.processMessageReceived(bobFlowKey, bobSessionState, bobSessionEvent1, instant)
        bobSessionState = bobResult1.sessionState
        assertThat(bobSessionState?.receivedEventsState?.lastProcessedSequenceNum).isEqualTo(1)
        assertThat(bobSessionState?.status).isEqualTo(SessionStateType.CONFIRMED)
        assertThat(bobResult1.outputSessionEvents).isNotNull

        //receive out of order data
        val bobResult2 = sessionManager.processMessageReceived(bobFlowKey, bobSessionState, bobSessionEvent2, instant)
        bobSessionState = bobResult2.sessionState
        assertThat(bobSessionState?.receivedEventsState?.lastProcessedSequenceNum).isEqualTo(3)
        assertThat(bobSessionState?.status).isEqualTo(SessionStateType.CONFIRMED)
        assertThat(bobResult2.outputSessionEvents).isNotNull

        var bobNextEvent = sessionManager.getNextReceivedEvent(bobSessionState!!)
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