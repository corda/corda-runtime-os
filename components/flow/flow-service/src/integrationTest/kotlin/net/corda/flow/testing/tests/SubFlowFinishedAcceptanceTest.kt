package net.corda.flow.testing.tests

import net.corda.data.flow.FlowStackItem
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.testing.context.FlowServiceTestBase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.osgi.test.junit5.service.ServiceExtension

@ExtendWith(ServiceExtension::class)
@Execution(ExecutionMode.SAME_THREAD)
class SubFlowFinishedAcceptanceTest : FlowServiceTestBase() {

    @BeforeEach
    fun beforeEach() {
        given {
            virtualNode(CPI1, ALICE_HOLDING_IDENTITY)
            cpkMetadata(CPI1, CPK1)
            sandboxCpk(CPK1)
            membershipGroupFor(ALICE_HOLDING_IDENTITY)

            sessionInitiatingIdentity(ALICE_HOLDING_IDENTITY)
            sessionInitiatedIdentity(BOB_HOLDING_IDENTITY)
        }
    }

    @Test
    fun `Given a subFlow contains only initiated sessions when the subFlow finishes session close events are sent`() {
        given {
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
                .suspendsWith(FlowIORequest.InitiateFlow(initiatedIdentityMemberName, SESSION_ID_1))

            sessionAckEventReceived(FLOW_ID1, SESSION_ID_1, receivedSequenceNum = 1)
                .suspendsWith(FlowIORequest.InitiateFlow(initiatedIdentityMemberName, SESSION_ID_2))
        }

        `when` {
            sessionAckEventReceived(FLOW_ID1, SESSION_ID_2, receivedSequenceNum = 1)
                .suspendsWith(FlowIORequest.SubFlowFinished(initiatingFlowStackItem(SESSION_ID_1, SESSION_ID_2)))
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                sessionCloseEvents(SESSION_ID_1, SESSION_ID_2)
            }
        }
    }

    @Test
    fun `Given a subFlow contains an initiated and closed session when the subFlow finishes a single session close event is sent`() {
        given {
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
                .suspendsWith(FlowIORequest.InitiateFlow(initiatedIdentityMemberName, SESSION_ID_1))

            sessionAckEventReceived(FLOW_ID1, SESSION_ID_1, receivedSequenceNum = 1)
                .suspendsWith(FlowIORequest.InitiateFlow(initiatedIdentityMemberName, SESSION_ID_2))

            sessionAckEventReceived(FLOW_ID1, SESSION_ID_2, receivedSequenceNum = 1)
                .suspendsWith(FlowIORequest.CloseSessions(setOf(SESSION_ID_1)))
        }

        `when` {
            sessionCloseEventReceived(FLOW_ID1, SESSION_ID_1, sequenceNum = 1, receivedSequenceNum = 2)
                .suspendsWith(FlowIORequest.SubFlowFinished(initiatingFlowStackItem(SESSION_ID_1, SESSION_ID_2)))
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                sessionCloseEvents(SESSION_ID_2)
            }
        }
    }

    @Test
    fun `Given a subFlow contains only closed sessions when the subFlow finishes a wakeup event is scheduled`() {
        given {
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
                .suspendsWith(FlowIORequest.InitiateFlow(initiatedIdentityMemberName, SESSION_ID_1))

            sessionAckEventReceived(FLOW_ID1, SESSION_ID_1, receivedSequenceNum = 1)
                .suspendsWith(FlowIORequest.InitiateFlow(initiatedIdentityMemberName, SESSION_ID_2))

            sessionAckEventReceived(FLOW_ID1, SESSION_ID_2, receivedSequenceNum = 1)
                .suspendsWith(FlowIORequest.CloseSessions(setOf(SESSION_ID_1, SESSION_ID_2)))

            sessionCloseEventReceived(FLOW_ID1, SESSION_ID_1, sequenceNum = 1, receivedSequenceNum = 2)
        }

        `when` {
            sessionCloseEventReceived(FLOW_ID1, SESSION_ID_2, sequenceNum = 1, receivedSequenceNum = 2)
                .suspendsWith(FlowIORequest.SubFlowFinished(initiatingFlowStackItem(SESSION_ID_1, SESSION_ID_2)))
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                sessionCloseEvents()
                wakeUpEvent()
            }
        }
    }

    private fun initiatingFlowStackItem(vararg sessionIds: String): FlowStackItem {
        return FlowStackItem(FLOW_NAME, true, sessionIds.toList())
    }
}