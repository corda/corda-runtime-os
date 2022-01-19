package net.corda.session.manager.integration

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionClose
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.event.session.SessionInit
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

    //TODO individual tests per message type, complex scenarios and error scenarios

    @Test
    fun testFullSessionSendAndReceive() {
        //=============== Init =======================

        //send INIT
        val aliceSessionInit = SessionInit(flowName, cpiId, aliceFlowKey, bobIdentity, aliceIdentity, null)
        val aliceSessionEvent1 = SessionEvent(MessageDirection.OUTBOUND, instant.toEpochMilli(), null, null, aliceSessionInit)
        val aliceResultInit = sessionManager.processMessage(aliceFlowKey, null, aliceSessionEvent1, instant)
        var aliceSessionState = aliceResultInit.sessionState
        assertThat(aliceSessionState).isNotNull
        assertThat(aliceResultInit.outputSessionRecord).isNotNull

        //FlowMapper Changes
        val bobSessionInitReceived = aliceResultInit.outputSessionRecord?.value?.payload as SessionEvent
        bobSessionInitReceived.messageDirection = MessageDirection.INBOUND
        val bobSessionInit = bobSessionInitReceived.payload as SessionInit
        bobSessionInit.flowKey = null

        //Receive Init
        val bobResultInitResult = sessionManager.processMessage(bobFlowKey, null, bobSessionInitReceived, instant)
        var bobSessionState = bobResultInitResult.sessionState
        assertThat(bobSessionState).isNotNull
        assertThat(bobResultInitResult.outputSessionRecord).isNotNull

        //FlowMapper Changes
        val aliceSessionEventAck1 = bobResultInitResult.outputSessionRecord?.value?.payload as SessionEvent
        aliceSessionEventAck1.messageDirection = MessageDirection.INBOUND

        //Receive Ack
        val aliceResultAck1Result = sessionManager.processMessage(aliceFlowKey, aliceSessionState, aliceSessionEventAck1, instant)
        aliceSessionState = aliceResultAck1Result.sessionState
        assertThat(aliceSessionState).isNotNull
        assertThat(aliceResultAck1Result.outputSessionRecord).isNull()

        //bob processes init
        bobSessionState = sessionManager.acknowledgeReceivedEvent(bobSessionState!!, bobSessionInitReceived.sequenceNum)
        assertThat(bobSessionState.receivedEventsState.undeliveredMessages).isEmpty()

        //=============== Data =======================
        //send data
        val aliceSessionData = SessionData(null)
        val aliceSessionEvent2 = SessionEvent(MessageDirection.OUTBOUND, instant.toEpochMilli(), sessionIdInitiated, null, aliceSessionData)
        val aliceResultDataSentResult = sessionManager.processMessage(aliceFlowKey, aliceSessionState, aliceSessionEvent2, instant)
        aliceSessionState = aliceResultDataSentResult.sessionState
        assertThat(aliceSessionState).isNotNull
        assertThat(aliceResultDataSentResult.outputSessionRecord).isNotNull

        //mapper changes
        val bobSessionDataReceived = aliceResultDataSentResult.outputSessionRecord?.value?.payload as SessionEvent
        bobSessionDataReceived.messageDirection = MessageDirection.INBOUND

        //data received
        val bobResultDataReceivedResult = sessionManager.processMessage(bobFlowKey, bobSessionState, bobSessionDataReceived, instant)
        bobSessionState = bobResultDataReceivedResult.sessionState
        assertThat(bobSessionState).isNotNull
        assertThat(bobResultDataReceivedResult.outputSessionRecord).isNotNull

        //FlowMapper Changes
        val aliceSessionEventAck2 = bobResultDataReceivedResult.outputSessionRecord?.value?.payload as SessionEvent
        aliceSessionEventAck2.messageDirection = MessageDirection.INBOUND

        //Receive Ack
        val aliceResultAck2Result = sessionManager.processMessage(aliceFlowKey, aliceSessionState, aliceSessionEventAck2, instant)
        aliceSessionState = aliceResultAck2Result.sessionState
        assertThat(aliceSessionState).isNotNull
        assertThat(aliceResultAck2Result.outputSessionRecord).isNull()

        val bobSessionEventReceived = sessionManager.getNextReceivedEvent(bobSessionState)
        assertThat(bobSessionEventReceived).isNotNull

        //bob processes data
        bobSessionState = sessionManager.acknowledgeReceivedEvent(bobSessionState!!, bobSessionEventReceived!!.sequenceNum)
        assertThat(bobSessionState.receivedEventsState.undeliveredMessages).isEmpty()

        //=============== ALice  Close =======================
        //Alice send close
        val aliceSessionClose = SessionClose()
        val aliceSessionEvent3= SessionEvent(MessageDirection.OUTBOUND, instant.toEpochMilli(), sessionIdInitiated, null, aliceSessionClose)
        val aliceResultCloseSent = sessionManager.processMessage(aliceFlowKey, aliceSessionState, aliceSessionEvent3, instant)
        aliceSessionState = aliceResultCloseSent.sessionState
        assertThat(aliceSessionState).isNotNull
        assertThat(aliceResultCloseSent.outputSessionRecord).isNotNull

        //mapper changes
        val bobSessionCloseReceived = aliceResultCloseSent.outputSessionRecord?.value?.payload as SessionEvent
        bobSessionCloseReceived.messageDirection = MessageDirection.INBOUND

        //receive close
        val bobResultCloseReceivedResult = sessionManager.processMessage(bobFlowKey, bobSessionState, bobSessionCloseReceived, instant)
        bobSessionState = bobResultCloseReceivedResult.sessionState
        assertThat(bobSessionState).isNotNull
        assertThat(bobResultCloseReceivedResult.outputSessionRecord).isNotNull

        //FlowMapper Changes
        val aliceSessionEventAck3 = bobResultCloseReceivedResult.outputSessionRecord?.value?.payload as SessionEvent
        aliceSessionEventAck3.messageDirection = MessageDirection.INBOUND

        //Receive Ack
        val aliceResultAck3Result = sessionManager.processMessage(aliceFlowKey, aliceSessionState, aliceSessionEventAck3, instant)
        aliceSessionState = aliceResultAck3Result.sessionState
        assertThat(aliceSessionState).isNotNull
        assertThat(aliceResultAck3Result.outputSessionRecord).isNull()

        //=============== Bob  Close =======================
        //bob send close
        val bobSessionClose = SessionClose()
        val bobSessionEvent = SessionEvent(MessageDirection.OUTBOUND, instant.toEpochMilli(), sessionIdInitiated, null, bobSessionClose)
        val bobResultCloseSentResult = sessionManager.processMessage(bobFlowKey, bobSessionState, bobSessionEvent, instant)
        bobSessionState = bobResultCloseSentResult.sessionState
        assertThat(bobSessionState).isNotNull
        assertThat(bobResultCloseSentResult.outputSessionRecord).isNotNull

        //mapper changes
        val aliceSessionCloseReceived = bobResultCloseSentResult.outputSessionRecord?.value?.payload as SessionEvent
        aliceSessionCloseReceived.messageDirection = MessageDirection.INBOUND

        //alice receive close
        val aliceResultCloseReceivedResult = sessionManager.processMessage(aliceFlowKey, aliceSessionState, aliceSessionCloseReceived, instant)
        aliceSessionState = aliceResultCloseReceivedResult.sessionState
        assertThat(aliceSessionState).isNotNull
        assertThat(aliceSessionState?.status).isEqualTo(SessionStateType.CLOSED)
        assertThat(aliceResultCloseReceivedResult.outputSessionRecord).isNotNull

        //FlowMapper Changes
        val bobSessionEventAck = aliceResultCloseReceivedResult.outputSessionRecord?.value?.payload as SessionEvent
        bobSessionEventAck.messageDirection = MessageDirection.INBOUND

        //bob Receive Ack
        val bobResultAck3Result = sessionManager.processMessage(bobFlowKey, bobSessionState, bobSessionEventAck, instant)
        bobSessionState = bobResultAck3Result.sessionState
        assertThat(bobSessionState).isNotNull
        assertThat(bobSessionState?.status).isEqualTo(SessionStateType.CLOSED)
        assertThat(bobResultAck3Result.outputSessionRecord).isNull()
    }

    @Test
    fun testSendDataNullStateAndReceiveError() {
        val aliceSessionData = SessionData(null)
        val aliceSessionEvent = SessionEvent(MessageDirection.OUTBOUND, instant.toEpochMilli(), sessionIdInitiated, 2, aliceSessionData)
        val aliceResult = sessionManager.processMessage(aliceFlowKey, null, aliceSessionEvent, instant)

        //FlowMapper Changes
        val bobSessionEvent = aliceResult.outputSessionRecord?.value?.payload as SessionEvent
        bobSessionEvent.messageDirection = MessageDirection.INBOUND

        val bobResult = sessionManager.processMessage(bobFlowKey, SessionState(), bobSessionEvent, instant)
        assertThat(bobResult.sessionState?.status).isEqualTo(SessionStateType.ERROR)
        assertThat(bobResult.outputSessionRecord).isNull()
    }
}