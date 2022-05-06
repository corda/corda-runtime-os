package net.corda.flow.testing.tests

import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.testing.context.FlowServiceTestBase
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.osgi.test.junit5.service.ServiceExtension

@ExtendWith(ServiceExtension::class)
@Execution(ExecutionMode.SAME_THREAD)
class SendAndReceiveAcceptanceTest : FlowServiceTestBase() {

    private companion object {
        val DATA_MESSAGE_3 = byteArrayOf(3)
        val DATA_MESSAGE_4 = byteArrayOf(4)
    }

    @Test
    fun `(SendAndReceive) Calling 'sendAndReceive' on an initiated session sends a session data event`() {
        given {
            virtualNode(CPI1, ALICE_HOLDING_IDENTITY)
            cpkMetadata(CPI1, CPK1)
            sandboxCpk(CPK1)
            membershipGroupFor(ALICE_HOLDING_IDENTITY)

            sessionInitiatingIdentity(ALICE_HOLDING_IDENTITY)
            sessionInitiatedIdentity(BOB_HOLDING_IDENTITY)

            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
                .suspendsWith(FlowIORequest.InitiateFlow(initiatedIdentityMemberName, SESSION_ID_1))

            sessionAckEventReceived(FLOW_ID1, SESSION_ID_1, receivedSequenceNum = 1)
                .suspendsWith(FlowIORequest.InitiateFlow(initiatedIdentityMemberName, SESSION_ID_2))
        }

        `when` {
            sessionAckEventReceived(FLOW_ID1, SESSION_ID_2, receivedSequenceNum = 1)
                .suspendsWith(FlowIORequest.Send(mapOf(SESSION_ID_1 to DATA_MESSAGE_1, SESSION_ID_2 to DATA_MESSAGE_2)))
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                sessionDataEvents(SESSION_ID_1 to DATA_MESSAGE_1, SESSION_ID_2 to DATA_MESSAGE_2)
            }
        }
    }

    @Test
    fun `(SendAndReceive) Given a flow resumes after receiving session data events calling 'sendAndReceive' on the sessions sends session data events and no session ack for the session that resumed the flow`() {
        given {
            virtualNode(CPI1, ALICE_HOLDING_IDENTITY)
            cpkMetadata(CPI1, CPK1)
            sandboxCpk(CPK1)
            membershipGroupFor(ALICE_HOLDING_IDENTITY)

            sessionInitiatingIdentity(ALICE_HOLDING_IDENTITY)
            sessionInitiatedIdentity(BOB_HOLDING_IDENTITY)

            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
                .suspendsWith(FlowIORequest.InitiateFlow(initiatedIdentityMemberName, SESSION_ID_1))

            sessionAckEventReceived(FLOW_ID1, SESSION_ID_1, receivedSequenceNum = 1)
                .suspendsWith(FlowIORequest.InitiateFlow(initiatedIdentityMemberName, SESSION_ID_2))

            sessionAckEventReceived(FLOW_ID1, SESSION_ID_2, receivedSequenceNum = 1)
                .suspendsWith(FlowIORequest.Receive(setOf(SESSION_ID_1, SESSION_ID_2)))
        }

        `when` {
            sessionDataEventReceived(FLOW_ID1, SESSION_ID_1, DATA_MESSAGE_1, sequenceNum = 1, receivedSequenceNum = 1)

            sessionDataEventReceived(FLOW_ID1, SESSION_ID_2, DATA_MESSAGE_2, sequenceNum = 1, receivedSequenceNum = 1)
                .suspendsWith(FlowIORequest.SendAndReceive(mapOf(SESSION_ID_1 to DATA_MESSAGE_3, SESSION_ID_2 to DATA_MESSAGE_4)))
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
                sessionAckEvents(SESSION_ID_1)
            }

            expectOutputForFlow(FLOW_ID1) {
                flowResumedWith(mapOf(SESSION_ID_1 to DATA_MESSAGE_1, SESSION_ID_2 to DATA_MESSAGE_2))
                sessionDataEvents(SESSION_ID_1 to DATA_MESSAGE_3, SESSION_ID_2 to DATA_MESSAGE_4)
                sessionAckEvents()
            }
        }
    }
}