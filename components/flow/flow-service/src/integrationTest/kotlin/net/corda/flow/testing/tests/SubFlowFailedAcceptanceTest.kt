package net.corda.flow.testing.tests

import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.testing.context.FlowServiceTestBase
import net.corda.flow.testing.context.startFlow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.osgi.test.junit5.service.ServiceExtension

@ExtendWith(ServiceExtension::class)
@Execution(ExecutionMode.SAME_THREAD)
@Disabled
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
        `when` {
            startFlow(this)
                .suspendsWith(
                    FlowIORequest.SubFlowFailed(
                        RuntimeException(),
                        listOf(SESSION_ID_1, SESSION_ID_2)
                    )
                )
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                sessionErrorEvents(SESSION_ID_1, SESSION_ID_2)
                scheduleFlowMapperCleanupEvents(SESSION_ID_1, SESSION_ID_2)
            }
        }
    }

    @Test
    fun `Given a subFlow contains an initiated and closed session when the subFlow fails a wakeup event is scheduled a single session error event is sent to the initiated session and session cleanup is schedule`() {
        given {
            startFlow(this)
                .suspendsWith(FlowIORequest.CloseSessions(setOf(SESSION_ID_1)))
        }

        `when` {
            sessionCloseEventReceived(FLOW_ID1, SESSION_ID_1, sequenceNum = 1)
                .suspendsWith(
                    FlowIORequest.SubFlowFailed(
                        RuntimeException(),
                        listOf(SESSION_ID_1, SESSION_ID_2)
                    )
                )
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                sessionErrorEvents(SESSION_ID_2)
                singleOutputEvent()
                scheduleFlowMapperCleanupEvents(SESSION_ID_1, SESSION_ID_2)
            }
        }
    }

    @Test
    fun `Given a subFlow contains only closed sessions when the subFlow fails a wakeup event is scheduled and no session error events are sent`() {
        given {
            startFlow(this)
                .suspendsWith(FlowIORequest.CloseSessions(setOf(SESSION_ID_1, SESSION_ID_2)))

            sessionCloseEventReceived(FLOW_ID1, SESSION_ID_1, sequenceNum = 1)
        }

        `when` {
            sessionCloseEventReceived(FLOW_ID1, SESSION_ID_2, sequenceNum = 1)
                .suspendsWith(
                    FlowIORequest.SubFlowFailed(
                        RuntimeException(),
                        listOf(SESSION_ID_1, SESSION_ID_2)
                    )
                )
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                sessionErrorEvents()
                singleOutputEvent()
            }
        }
    }

    @Test
    fun `Given a subFlow contains only errored sessions when the subFlow fails a wakeup event is scheduled and no session error events are sent`() {
        given {
            startFlow(this)
                .suspendsWith(FlowIORequest.ForceCheckpoint)

            sessionErrorEventReceived(FLOW_ID1, SESSION_ID_1)
                .suspendsWith(FlowIORequest.ForceCheckpoint)
        }

        `when` {
            sessionErrorEventReceived(FLOW_ID1, SESSION_ID_2)
                .suspendsWith(
                    FlowIORequest.SubFlowFailed(
                        RuntimeException(),
                        listOf(SESSION_ID_1, SESSION_ID_2)
                    )
                )
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                sessionErrorEvents()
                singleOutputEvent()
            }
        }
    }

    @Test
    fun `Given a subFlow contains no sessions when the subFlow fails a wakeup event is scheduled`() {
        given {
            startFlow(this)
                .suspendsWith(FlowIORequest.ForceCheckpoint)
        }

        `when` {
       /*     sessionAckEventReceived(FLOW_ID1, SESSION_ID_2, receivedSequenceNum = 2)
                .suspendsWith(FlowIORequest.SubFlowFailed(RuntimeException(), emptyList()))
*/        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                sessionErrorEvents()
                singleOutputEvent()
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
                        listOf(INITIATED_SESSION_ID_1)
                    )
                )
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                singleOutputEvent()
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
            sessionCloseEventReceived(FLOW_ID1, INITIATED_SESSION_ID_1, sequenceNum = 2)
                .suspendsWith(
                    FlowIORequest.SubFlowFailed(
                        RuntimeException(),
                        listOf(INITIATED_SESSION_ID_1)
                    )
                )
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                sessionErrorEvents()
                singleOutputEvent()
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
            sessionErrorEventReceived(FLOW_ID1, INITIATED_SESSION_ID_1)
                .suspendsWith(
                    FlowIORequest.SubFlowFailed(
                        RuntimeException(),
                        listOf(INITIATED_SESSION_ID_1)
                    )
                )
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                sessionErrorEvents()
                singleOutputEvent()
            }
        }
    }
}