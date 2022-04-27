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
class SessionEventHandlingTest: FlowServiceTestBase() {

    @Test
    fun `(Receive) Given two sessions receiving all session data events resumes the flow and sends session acks`() {
        given {
            // Background setup
            virtualNode(CPI1, BOB_HOLDING_IDENTITY)
            cpkMetadata(CPI1, CPK1)
            sandboxCpk(CPK1)
            membershipGroupFor(BOB_HOLDING_IDENTITY)

            // Bob will be initiating sessions with Alice
            sessionInitiatingIdentity(BOB_HOLDING_IDENTITY)
            sessionInitiatedIdentity(ALICE_HOLDING_IDENTITY)

            // Bob starts a flow an initiates a session with Alice
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, BOB_HOLDING_IDENTITY, CPI1, "flow start data")
                .suspendsWith(FlowIORequest.InitiateFlow(initiatedIdentityMemberName, SESSION_ID_1))

            // Alice acknowledges the session and Bob creates a second session
            sessionAckEventReceived(FLOW_ID1, SESSION_ID_1,0)
                .suspendsWith(FlowIORequest.InitiateFlow(initiatedIdentityMemberName, SESSION_ID_2))

            // Alice acknowledges the second session and Bob requests to receive data for both
            // sessions
            sessionAckEventReceived(FLOW_ID1, SESSION_ID_2, 0)
                .suspendsWith(FlowIORequest.Receive(setOf(SESSION_ID_1, SESSION_ID_2)))
        }

        `when` {
            // Alice sends the data for session 1
            sessionDataEventReceived(FLOW_ID1, SESSION_ID_1, DATA_MESSAGE_1,1)

            // Alice sends the data for session 2
            sessionDataEventReceived(FLOW_ID1, SESSION_ID_2, DATA_MESSAGE_2, 1)
                .suspendsWith(FlowIORequest.ForceCheckpoint)
        }

        then {
            // As Bob has requested receive for both sessions we expect Bob to acknowledge the
            // receipt of the data for session 1 but not wake up the flow until all data is received.
            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
                sessionAckEvent(FLOW_ID1, SESSION_ID_1)
            }

            expectOutputForFlow(FLOW_ID1) {
                flowResumedWithSessionData(SESSION_ID_1 to DATA_MESSAGE_1, SESSION_ID_2 to DATA_MESSAGE_2)
                sessionAckEvent(FLOW_ID1, SESSION_ID_2)
                wakeUpEvent()
            }
        }
    }
}