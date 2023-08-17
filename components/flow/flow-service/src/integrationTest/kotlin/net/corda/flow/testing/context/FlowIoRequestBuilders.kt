package net.corda.flow.testing.context

import net.corda.flow.testing.tests.ALICE_HOLDING_IDENTITY
import net.corda.flow.testing.tests.CPI1
import net.corda.flow.testing.tests.DATA_MESSAGE_0
import net.corda.flow.testing.tests.FLOW_ID1
import net.corda.flow.testing.tests.REQUEST_ID1
import net.corda.flow.testing.tests.SESSION_ID_2

//todo - CORE-15757
/**
 * Initiate a single flow for [ALICE_HOLDING_IDENTITY] by triggering a send of [DATA_MESSAGE_0] to the [setup]s initiatedIdentityMemberName.
 * Receives an ack back with the sequence number set to [receivedAckSeqNum]. Defaults to 1 to indicate only the init message has been
 * processed so far. Set to 2 to confirm both the init and data messages sent by [ALICE_HOLDING_IDENTITY]
 */
fun initiateSingleFlow(setup: StepSetup): FlowIoRequestSetup {
    return setup.startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")

}

/**
 * Initiate a twos flow for [ALICE_HOLDING_IDENTITY] by triggering a send of [DATA_MESSAGE_0] to the [setup]s initiatedIdentityMemberName
 * twice.
 * Receives acks for both the init and send messages for the first session.
 * For the second session it receives an ack back with the sequence number set to [receivedAckSeqNumSecondSesion]. Defaults to 1 to indicate only the
 * init message has been processed so far for [SESSION_ID_2]. Set to 2 to confirm both the init and data messages sent by [ALICE_HOLDING_IDENTITY]
 */
fun initiateTwoFlows(setup: StepSetup): FlowIoRequestSetup {
    return setup.startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
}

