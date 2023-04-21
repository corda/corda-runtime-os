package net.corda.flow.testing.context

import net.corda.flow.application.sessions.SessionInfo
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.testing.tests.ALICE_HOLDING_IDENTITY
import net.corda.flow.testing.tests.CPI1
import net.corda.flow.testing.tests.DATA_MESSAGE_0
import net.corda.flow.testing.tests.FLOW_ID1
import net.corda.flow.testing.tests.REQUEST_ID1
import net.corda.flow.testing.tests.SESSION_ID_1
import net.corda.flow.testing.tests.SESSION_ID_2

/**
 * Initiate a single flow for [ALICE_HOLDING_IDENTITY] by triggering a send of [DATA_MESSAGE_0] to the [setup]s initiatedIdentityMemberName.
 * Receives an ack back with the sequence number set to [receivedAckSeqNum]. Defaults to 1 to indicate only the init message has been
 * processed so far. Set to 2 to confirm both the init and data messages sent by [ALICE_HOLDING_IDENTITY]
 */
fun initiateSingleFlow(setup: StepSetup, receivedAckSeqNum: Int = 1): FlowIoRequestSetup {
    setup.startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
        .suspendsWith(FlowIORequest.Send(mapOf(SessionInfo(SESSION_ID_1, setup.initiatedIdentityMemberName) to DATA_MESSAGE_0)))

    return setup.sessionAckEventReceived(FLOW_ID1, SESSION_ID_1, receivedSequenceNum = receivedAckSeqNum)
}

/**
 * Initiate a twos flow for [ALICE_HOLDING_IDENTITY] by triggering a send of [DATA_MESSAGE_0] to the [setup]s initiatedIdentityMemberName
 * twice.
 * Receives acks for both the init and send messages for the first session.
 * For the second session it receives an ack back with the sequence number set to [receivedAckSeqNumSecondSesion]. Defaults to 1 to indicate only the
 * init message has been processed so far for [SESSION_ID_2]. Set to 2 to confirm both the init and data messages sent by [ALICE_HOLDING_IDENTITY]
 */
fun initiateTwoFlows(setup: StepSetup, receivedAckSeqNumSecondSesion: Int = 1): FlowIoRequestSetup {
    setup.startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
        .suspendsWith(FlowIORequest.Send(mapOf(
            SessionInfo(SESSION_ID_1, setup.initiatedIdentityMemberName) to DATA_MESSAGE_0,
            SessionInfo(SESSION_ID_2, setup.initiatedIdentityMemberName) to DATA_MESSAGE_0),
        ))

    setup.sessionAckEventReceived(FLOW_ID1, SESSION_ID_1, receivedSequenceNum = 2)
    return setup.sessionAckEventReceived(FLOW_ID1, SESSION_ID_2, receivedSequenceNum = receivedAckSeqNumSecondSesion)
}

