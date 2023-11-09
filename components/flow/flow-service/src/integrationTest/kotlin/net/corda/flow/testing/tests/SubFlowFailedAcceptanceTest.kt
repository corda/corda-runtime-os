package net.corda.flow.testing.tests

import net.corda.data.flow.output.FlowStates
import net.corda.flow.application.sessions.SessionInfo
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.exceptions.FlowProcessingExceptionTypes.FLOW_FAILED
import net.corda.flow.testing.context.ALICE_FLOW_KEY_MAPPER
import net.corda.flow.testing.context.FlowServiceTestBase
import net.corda.flow.testing.context.startFlow
import net.corda.v5.base.exceptions.CordaRuntimeException
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
    fun `Given a subFlow contains sessions when the subFlow fails, session error events are sent and session cleanup is scheduled`() {
        `when` {
            startFlow(this)
                .suspendsWith(
                    FlowIORequest.Send(
                        mapOf(
                            SessionInfo(SESSION_ID_1, initiatedIdentityMemberName) to DATA_MESSAGE_1,
                            SessionInfo(SESSION_ID_2, initiatedIdentityMemberName) to DATA_MESSAGE_2,
                        )
                    )
                )
                .suspendsWith(
                    FlowIORequest.SubFlowFailed(
                        RuntimeException(),
                        listOf(SESSION_ID_1, SESSION_ID_2)
                    )
                )
                .completedWithError(CordaRuntimeException("error"))
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                sessionErrorEvents(SESSION_ID_1, SESSION_ID_2)
                scheduleFlowMapperCleanupEvents(ALICE_FLOW_KEY_MAPPER, SESSION_ID_1, SESSION_ID_2)
            }
        }
    }

    @Test
    fun `Given a subFlow contains an initiated and closed session when the subFlow fails a single session error event is sent to the initiated session and session cleanup is scheduled`() {
        `when` {
            startFlow(this)
                .suspendsWith(
                    FlowIORequest.Send(
                        mapOf(
                            SessionInfo(SESSION_ID_1, initiatedIdentityMemberName) to DATA_MESSAGE_1,
                            SessionInfo(SESSION_ID_2, initiatedIdentityMemberName) to DATA_MESSAGE_2,
                        )
                    )
                )
                .suspendsWith(FlowIORequest.CloseSessions(setOf(SESSION_ID_1)))

            sessionCloseEventReceived(FLOW_ID1, SESSION_ID_1, 1, ALICE_HOLDING_IDENTITY, BOB_HOLDING_IDENTITY)
                .suspendsWith(
                    FlowIORequest.SubFlowFailed(
                        RuntimeException(),
                        listOf(SESSION_ID_1, SESSION_ID_2)
                    )
                )
                .completedWithError(CordaRuntimeException("error"))
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                noOutputEvent()
            }

            expectOutputForFlow(FLOW_ID1) {
                sessionErrorEvents(SESSION_ID_2)
                scheduleFlowMapperCleanupEvents(ALICE_FLOW_KEY_MAPPER, SESSION_ID_1, SESSION_ID_2)
            }
        }
    }


    @Test
    fun `Given a subFlow contains only closed sessions when the subFlow fails no session error events are sent`() {
        `when` {
            startFlow(this)
                .suspendsWith(
                    FlowIORequest.Send(
                        mapOf(
                            SessionInfo(SESSION_ID_1, initiatedIdentityMemberName) to DATA_MESSAGE_1,
                            SessionInfo(SESSION_ID_2, initiatedIdentityMemberName) to DATA_MESSAGE_2,
                        )
                    )
                )
                .suspendsWith(FlowIORequest.CloseSessions(setOf(SESSION_ID_1, SESSION_ID_2)))
                .suspendsWith(
                    FlowIORequest.SubFlowFailed(
                        RuntimeException(),
                        listOf(SESSION_ID_1, SESSION_ID_2)
                    )
                )
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                noFlowEvents()
            }
        }
    }

    @Test
    fun `Given a subFlow contains only errored sessions when the subFlow fails no session error events are sent`() {
        given {
            startFlow(this)
                .suspendsWith(
                    FlowIORequest.Receive(
                        setOf(
                            SessionInfo(SESSION_ID_1, initiatedIdentityMemberName),
                            SessionInfo(SESSION_ID_2, initiatedIdentityMemberName),
                        )
                    )
                )

            sessionErrorEventReceived(FLOW_ID1, SESSION_ID_1)
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
                noFlowEvents()
            }
        }
    }

    @Test
    fun `Given a subFlow contains no sessions when the subFlow fails and flow finishes, requestid is cleaned up and no session errors are sent`() {
        `when` {
            startFlow(this)
                .suspendsWith(FlowIORequest.SubFlowFailed(RuntimeException(), emptyList()))
                .completedWithError(CordaRuntimeException("Error"))
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                scheduleFlowMapperCleanupEvents(ALICE_FLOW_KEY_MAPPER)
                sessionErrorEvents()
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
            sessionCounterpartyInfoRequestReceived(FLOW_ID1, INITIATED_SESSION_ID_1, CPI1, PROTOCOL_2)
                .suspendsWith(
                    FlowIORequest.SubFlowFailed(
                        RuntimeException(),
                        listOf(INITIATED_SESSION_ID_1)
                    )
                )
                .completedWithError(CordaRuntimeException("Error"))
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                sessionErrorEvents(INITIATED_SESSION_ID_1)
                scheduleFlowMapperCleanupEvents(INITIATED_SESSION_ID_1)
            }
        }
    }

    @Test
    fun `Given an initiated top level flow with a closed session when it finishes and calls SubFlowFailed, schedules cleanup and does not send a session error event`() {
        given {
            membershipGroupFor(BOB_HOLDING_IDENTITY)
            initiatingToInitiatedFlow(PROTOCOL_2, FLOW_NAME, FLOW_NAME_2)
        }

        `when` {
            sessionCounterpartyInfoRequestReceived(FLOW_ID1, INITIATED_SESSION_ID_1, CPI1, PROTOCOL_2)
                .suspendsWith(FlowIORequest.CloseSessions(setOf(INITIATED_SESSION_ID_1)))
                .suspendsWith(
                    FlowIORequest.SubFlowFailed(
                        RuntimeException(),
                        listOf(INITIATED_SESSION_ID_1)
                    )
                )
                .completedWithError(CordaRuntimeException("Error"))
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                sessionErrorEvents()
                scheduleFlowMapperCleanupEvents(INITIATED_SESSION_ID_1)
            }
        }
    }

    @Test
    fun `Given an initiated top level flow with an errored session when it finishes and calls SubFlowFailed, schedules cleanup and does not send a session error event`() {
        given {
            membershipGroupFor(BOB_HOLDING_IDENTITY)
            initiatingToInitiatedFlow(PROTOCOL_2, FLOW_NAME, FLOW_NAME_2)

            sessionCounterpartyInfoRequestReceived(FLOW_ID1, INITIATED_SESSION_ID_1, CPI1, PROTOCOL_2)
                .suspendsWith(
                    FlowIORequest.Receive(
                        setOf(
                            SessionInfo(INITIATED_SESSION_ID_1, initiatedIdentityMemberName),
                        )
                    )
                )
        }

        `when` {
            sessionErrorEventReceived(FLOW_ID1, INITIATED_SESSION_ID_1)
                .suspendsWith(
                    FlowIORequest.SubFlowFailed(
                        RuntimeException(),
                        listOf(INITIATED_SESSION_ID_1)
                    )
                )
                .completedWithError(CordaRuntimeException("Error"))
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                sessionErrorEvents()
                scheduleFlowMapperCleanupEvents(INITIATED_SESSION_ID_1)
            }
        }
    }

    @Test
    fun `Given a subFlow contains sessions when the subFlow fails, and session is not found, FlowFatalException is thrown`() {
        `when` {
            startFlow(this)
                .suspendsWith(
                    FlowIORequest.Send(
                        mapOf(
                            SessionInfo(SESSION_ID_1, initiatedIdentityMemberName) to DATA_MESSAGE_1,
                        )
                    )
                )
                .suspendsWith(
                    FlowIORequest.SubFlowFailed(
                        RuntimeException(),
                        listOf(SESSION_ID_1, "BrokenSession")
                    )
                )
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                sessionErrorEvents(SESSION_ID_1)
                scheduleFlowMapperCleanupEvents(SESSION_ID_1, ALICE_FLOW_KEY_MAPPER)
                nullStateRecord()
                flowStatus(state = FlowStates.FAILED,  errorType = FLOW_FAILED, errorMessage = "Session: BrokenSession does not exist when executing session operation that requires an existing session")
            }
        }
    }

}