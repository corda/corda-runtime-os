package net.corda.flow.testing.tests

import net.corda.flow.application.sessions.SessionInfo
import net.corda.flow.fiber.FlowIORequest
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

class SubFlowFinishedAcceptanceTest : FlowServiceTestBase() {

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
    fun `Given a subFlow that contains no sessions when the subFlow finishes flow completes successfully`() {
        `when` {
            startFlow(this)
                .suspendsWith(FlowIORequest.SubFlowFinished(emptyList()))
                .completedSuccessfullyWith("hello")
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                scheduleFlowMapperCleanupEvents(ALICE_FLOW_KEY_MAPPER)
            }
        }
    }

    @Suppress("MaxLineLength")
    @Test
    fun `when an initiating session subFlow with requireClose set to true closes a session and then the subFlow finishes, the flow completes and schedules cleanup`() {

        `when` {
            startFlow(this)
                .suspendsWith(FlowIORequest.Send(
                    mapOf(
                        SessionInfo(SESSION_ID_1, initiatedIdentityMemberName) to  DATA_MESSAGE_1,
                    )))
                .suspendsWith(FlowIORequest.CloseSessions(setOf(SESSION_ID_1)))

            sessionCloseEventReceived(FLOW_ID1, SESSION_ID_1, 1, ALICE_HOLDING_IDENTITY, BOB_HOLDING_IDENTITY)
                .suspendsWith(FlowIORequest.SubFlowFinished(listOf(SESSION_ID_1)))
                .completedSuccessfullyWith("hello")
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                sessionDataEvents(Pair(SESSION_ID_1, DATA_MESSAGE_1))
                sessionCloseEvents()
            }

            expectOutputForFlow(FLOW_ID1) {
                scheduleFlowMapperCleanupEvents(ALICE_FLOW_KEY_MAPPER, SESSION_ID_1)
                sessionCloseEvents()
            }
        }
    }

    @Suppress("MaxLineLength")
    @Test
    fun `when an initiating session subFlow with requireClose set to false closes a session and then the subFlow finishes, the flow completes and schedules cleanup`() {

        `when` {
            startFlow(this)
                .suspendsWith(FlowIORequest.Send(
                    mapOf(
                        SessionInfo(SESSION_ID_1, initiatedIdentityMemberName, requireClose = false) to  DATA_MESSAGE_1,
                    )))
                .suspendsWith(FlowIORequest.CloseSessions(setOf(SESSION_ID_1)))
                .suspendsWith(FlowIORequest.SubFlowFinished(listOf(SESSION_ID_1)))
                .completedSuccessfullyWith("hello")
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                sessionDataEvents(Pair(SESSION_ID_1, DATA_MESSAGE_1))
                scheduleFlowMapperCleanupEvents(ALICE_FLOW_KEY_MAPPER, SESSION_ID_1)
                sessionCloseEvents()
            }
        }
    }

    @Suppress("MaxLineLength")
    @Test
    fun `Given an initiated top level flow with requireClose set to true, when it finishes and calls SubFlowFinished it completes flow, sends a close, and schedules cleanup`() {
        given {
            membershipGroupFor(BOB_HOLDING_IDENTITY)
            initiatingToInitiatedFlow(PROTOCOL_2, FLOW_NAME, FLOW_NAME_2)
        }

        `when` {
            sessionCounterpartyInfoRequestReceived(FLOW_ID1, INITIATED_SESSION_ID_1, CPI1, PROTOCOL_2)
                .suspendsWith(FlowIORequest.CloseSessions(setOf(INITIATED_SESSION_ID_1)))
                .suspendsWith(
                    FlowIORequest.SubFlowFinished(listOf(INITIATED_SESSION_ID_1))
                )
                .completedSuccessfullyWith("hello")
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                scheduleFlowMapperCleanupEvents(INITIATED_SESSION_ID_1)
                sessionCloseEvents(INITIATED_SESSION_ID_1)
            }
        }
    }

    @Suppress("MaxLineLength")
    @Test
    fun `Given an initiated top level flow with requireClose set to false, when it finishes and calls SubFlowFinished it completes flow and schedules cleanup`() {
        given {
            membershipGroupFor(BOB_HOLDING_IDENTITY)
            initiatingToInitiatedFlow(PROTOCOL_2, FLOW_NAME, FLOW_NAME_2)
        }

        `when` {
            sessionCounterpartyInfoRequestReceived(FLOW_ID1, INITIATED_SESSION_ID_1, CPI1, PROTOCOL_2, requireClose = false)
                .suspendsWith(FlowIORequest.CloseSessions(setOf(INITIATED_SESSION_ID_1)))
                .suspendsWith(
                    FlowIORequest.SubFlowFinished(listOf(INITIATED_SESSION_ID_1))
                )
                .completedSuccessfullyWith("hello")
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                scheduleFlowMapperCleanupEvents(INITIATED_SESSION_ID_1)
                sessionCloseEvents()
            }
        }
    }

    @Suppress("MaxLineLength")
    @Test
    fun `Given an initiated top level flow with an errored session when it finishes and calls SubFlowFinished, cleanup is scheduled`() {
        given {
            membershipGroupFor(BOB_HOLDING_IDENTITY)
            initiatingToInitiatedFlow(PROTOCOL_2, FLOW_NAME, FLOW_NAME_2)

            sessionCounterpartyInfoRequestReceived(FLOW_ID1, INITIATED_SESSION_ID_1, CPI1, PROTOCOL_2)
                .suspendsWith(FlowIORequest.Receive(setOf(SessionInfo(INITIATED_SESSION_ID_1, initiatingIdentityMemberName))))
        }

        `when` {
            sessionErrorEventReceived(FLOW_ID1, INITIATED_SESSION_ID_1)
                .suspendsWith(
                    FlowIORequest.SubFlowFinished(listOf(INITIATED_SESSION_ID_1))
                )
                .completedWithError(CordaRuntimeException("error"))
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                scheduleFlowMapperCleanupEvents(INITIATED_SESSION_ID_1)
                sessionCloseEvents()
            }
        }
    }
}
