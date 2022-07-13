package net.corda.flow.testing.tests

import net.corda.data.flow.state.checkpoint.FlowStackItem
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
class SubFlowFailedAcceptanceTest : FlowServiceTestBase() {

    @BeforeEach
    fun beforeEach() {
        given {
            virtualNode(CPI1, ALICE_HOLDING_IDENTITY)
            cpkMetadata(CPI1, CPK1, CPK1_CHECKSUM)
            sandboxCpk(CPK1_CHECKSUM)
            membershipGroupFor(ALICE_HOLDING_IDENTITY)

            virtualNode(CPI1, BOB_HOLDING_IDENTITY)

            sessionInitiatingIdentity(ALICE_HOLDING_IDENTITY)
            sessionInitiatedIdentity(BOB_HOLDING_IDENTITY)

            initiatingToInitiatedFlow(PROTOCOL, FAKE_FLOW_NAME, FAKE_FLOW_NAME)
        }
    }

    @Test
    fun `Given a subFlow contains only initiated sessions when the subFlow fails a wakeup event is scheduled session error events are sent and session cleanup is scheduled`() {
        given {
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
                .suspendsWith(FlowIORequest.InitiateFlow(initiatedIdentityMemberName, SESSION_ID_1))

            sessionAckEventReceived(FLOW_ID1, SESSION_ID_1, receivedSequenceNum = 1)
                .suspendsWith(FlowIORequest.InitiateFlow(initiatedIdentityMemberName, SESSION_ID_2))
        }

        `when` {
            sessionAckEventReceived(FLOW_ID1, SESSION_ID_2, receivedSequenceNum = 1)
                .suspendsWith(FlowIORequest.SubFlowFailed(RuntimeException(), initiatingFlowStackItem(SESSION_ID_1, SESSION_ID_2)))
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                sessionErrorEvents(SESSION_ID_1, SESSION_ID_2)
                wakeUpEvent()
                scheduleFlowMapperCleanupEvents(SESSION_ID_1, SESSION_ID_2)
            }
        }
    }

    @Test
    fun `Given a subFlow contains an initiated and closed session when the subFlow fails a wakeup event is scheduled a single session error event is sent to the initiated session and session cleanup is schedule`() {
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
                .suspendsWith(FlowIORequest.SubFlowFailed(RuntimeException(), initiatingFlowStackItem(SESSION_ID_1, SESSION_ID_2)))
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                sessionErrorEvents(SESSION_ID_2)
                wakeUpEvent()
                scheduleFlowMapperCleanupEvents(SESSION_ID_1, SESSION_ID_2)
            }
        }
    }

    @Test
    fun `Given a subFlow contains only closed sessions when the subFlow fails a wakeup event is scheduled and no session error events are sent`() {
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
                .suspendsWith(FlowIORequest.SubFlowFailed(RuntimeException(), initiatingFlowStackItem(SESSION_ID_1, SESSION_ID_2)))
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                sessionErrorEvents()
                wakeUpEvent()
            }
        }
    }

    @Test
    fun `Given a subFlow contains only errored sessions when the subFlow fails a wakeup event is scheduled and no session error events are sent`() {
        given {
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
                .suspendsWith(FlowIORequest.InitiateFlow(initiatedIdentityMemberName, SESSION_ID_1))

            sessionAckEventReceived(FLOW_ID1, SESSION_ID_1, receivedSequenceNum = 1)
                .suspendsWith(FlowIORequest.InitiateFlow(initiatedIdentityMemberName, SESSION_ID_2))

            sessionAckEventReceived(FLOW_ID1, SESSION_ID_2, receivedSequenceNum = 1)
                .suspendsWith(FlowIORequest.ForceCheckpoint)

            sessionErrorEventReceived(FLOW_ID1, SESSION_ID_1, sequenceNum = 1, receivedSequenceNum = 2)
                .suspendsWith(FlowIORequest.ForceCheckpoint)
        }

        `when` {
            sessionErrorEventReceived(FLOW_ID1, SESSION_ID_2, sequenceNum = 1, receivedSequenceNum = 2)
                .suspendsWith(FlowIORequest.SubFlowFailed(RuntimeException(), initiatingFlowStackItem(SESSION_ID_1, SESSION_ID_2)))
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                sessionErrorEvents()
                wakeUpEvent()
            }
        }
    }

    @Test
    fun `Given a subFlow contains no sessions when the subFlow fails a wakeup event is scheduled`() {
        given {
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
                .suspendsWith(FlowIORequest.InitiateFlow(initiatedIdentityMemberName, SESSION_ID_1))

            sessionAckEventReceived(FLOW_ID1, SESSION_ID_1, receivedSequenceNum = 1)
                .suspendsWith(FlowIORequest.InitiateFlow(initiatedIdentityMemberName, SESSION_ID_2))
        }

        `when` {
            sessionAckEventReceived(FLOW_ID1, SESSION_ID_2, receivedSequenceNum = 1)
                .suspendsWith(FlowIORequest.SubFlowFailed(RuntimeException(), initiatingFlowStackItem()))
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                sessionErrorEvents()
                wakeUpEvent()
            }
        }
    }

    @Test
    fun `Given an initiated top level flow with an initiated session when it finishes and calls SubFlowFailed a session error event is sent and session cleanup is scheduled`() {
        given {
            membershipGroupFor(BOB_HOLDING_IDENTITY)
            initiatingToInitiatedFlow(PROTOCOL_2, FLOW_NAME, FLOW_NAME_2)
        }

        `when` {
            sessionInitEventReceived(FLOW_ID1, INITIATED_SESSION_ID_1, CPI1, PROTOCOL_2)
                .suspendsWith(
                    FlowIORequest.SubFlowFailed(
                        RuntimeException(),
                        FlowStackItem(FLOW_NAME, false, listOf(INITIATED_SESSION_ID_1))
                    )
                )
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                wakeUpEvent()
                sessionErrorEvents(INITIATED_SESSION_ID_1)
                scheduleFlowMapperCleanupEvents(INITIATED_SESSION_ID_1)
            }
        }
    }

    @Test
    fun `Given an initiated top level flow with a closed session when it finishes and calls SubFlowFailed a wakeup event is scheduled and does not send a session error event`() {
        given {
            membershipGroupFor(BOB_HOLDING_IDENTITY)
            initiatingToInitiatedFlow(PROTOCOL_2, FLOW_NAME, FLOW_NAME_2)

            sessionInitEventReceived(FLOW_ID1, INITIATED_SESSION_ID_1, CPI1, PROTOCOL_2)
                .suspendsWith(FlowIORequest.CloseSessions(setOf(INITIATED_SESSION_ID_1)))
        }

        `when` {
            sessionCloseEventReceived(FLOW_ID1, INITIATED_SESSION_ID_1, sequenceNum = 1, receivedSequenceNum = 2)
                .suspendsWith(
                    FlowIORequest.SubFlowFailed(
                        RuntimeException(),
                        FlowStackItem(FLOW_NAME, false, listOf(INITIATED_SESSION_ID_1))
                    )
                )
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                wakeUpEvent()
                sessionErrorEvents()
            }
        }
    }

    @Test
    fun `Given an initiated top level flow with an errored session when it finishes and calls SubFlowFailed a wakeup event is scheduled and no session error event is sent`() {
        given {
            membershipGroupFor(BOB_HOLDING_IDENTITY)
            initiatingToInitiatedFlow(PROTOCOL_2, FLOW_NAME, FLOW_NAME_2)

            sessionInitEventReceived(FLOW_ID1, INITIATED_SESSION_ID_1, CPI1, PROTOCOL_2)
                .suspendsWith(FlowIORequest.ForceCheckpoint)
        }

        `when` {
            sessionErrorEventReceived(FLOW_ID1, INITIATED_SESSION_ID_1, sequenceNum = 1, receivedSequenceNum = 1)
                .suspendsWith(
                    FlowIORequest.SubFlowFailed(
                        RuntimeException(),
                        FlowStackItem(FLOW_NAME, false, listOf(INITIATED_SESSION_ID_1))
                    )
                )
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                wakeUpEvent()
                sessionErrorEvents()
            }
        }
    }

    private fun initiatingFlowStackItem(vararg sessionIds: String): FlowStackItem {
        return FlowStackItem(FLOW_NAME, true, sessionIds.toList())
    }
}