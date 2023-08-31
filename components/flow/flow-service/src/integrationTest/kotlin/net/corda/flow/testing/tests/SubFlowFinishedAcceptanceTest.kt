package net.corda.flow.testing.tests

import net.corda.data.flow.event.session.SessionClose
import net.corda.data.flow.event.session.SessionData
import net.corda.flow.application.sessions.SessionInfo
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.testing.context.ALICE_FLOW_KEY
import net.corda.flow.testing.context.FlowServiceTestBase
import net.corda.flow.testing.context.StepSetup
import net.corda.flow.testing.context.startFlow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.params.provider.Arguments
import org.osgi.test.junit5.service.ServiceExtension
import java.util.stream.Stream

@ExtendWith(ServiceExtension::class)
@Execution(ExecutionMode.SAME_THREAD)
@Disabled
class SubFlowFinishedAcceptanceTest : FlowServiceTestBase() {

    private companion object {
        @JvmStatic
        fun unrelatedSessionEvents(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(
                    SessionData::class.simpleName,
                    { dsl: StepSetup ->
                        dsl.sessionDataEventReceived(
                            FLOW_ID1,
                            SESSION_ID_2,
                            DATA_MESSAGE_1,
                            sequenceNum = 1
                        )
                    }
                ),
                Arguments.of(
                    SessionClose::class.simpleName,
                    { dsl: StepSetup ->
                        dsl.sessionCloseEventReceived(
                            FLOW_ID1,
                            SESSION_ID_2,
                            sequenceNum = 1
                        )
                    }
                )
            )
        }
    }

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
    fun `Given a subFlow contains no sessions when the subFlow finishes flow completes successfully`() {
        `when` {
            startFlow(this)
                .suspendsWith(FlowIORequest.SubFlowFinished(emptyList()))
                .completedSuccessfullyWith("hello")
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                scheduleFlowMapperCleanupEvents(ALICE_FLOW_KEY)
            }
        }
    }

    @Test
    fun `Given a subFlow contains a closed session when the subFlow finishes flow completes and schedules cleanup`() {

        `when` {
            startFlow(this)
                .suspendsWith(FlowIORequest.Send(
                    mapOf(
                        SessionInfo(SESSION_ID_1, initiatedIdentityMemberName) to  DATA_MESSAGE_1,
                    )))
                .suspendsWith(FlowIORequest.CloseSessions(setOf(SESSION_ID_1)))
                .suspendsWith(FlowIORequest.SubFlowFinished(listOf(SESSION_ID_1)))
                .completedSuccessfullyWith("hello")
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                scheduleFlowMapperCleanupEvents(ALICE_FLOW_KEY, SESSION_ID_1)
            }
        }
    }

    @Test
    fun `Given an initiated top level flow with a closed session when it finishes and calls SubFlowFinished completes flow and schedules cleanup`() {
        given {
            membershipGroupFor(BOB_HOLDING_IDENTITY)
            initiatingToInitiatedFlow(PROTOCOL_2, FLOW_NAME, FLOW_NAME_2)
        }

        `when` {
            sessionInitEventReceived(FLOW_ID1, INITIATED_SESSION_ID_1, CPI1, PROTOCOL_2)
                .suspendsWith(FlowIORequest.CloseSessions(setOf(INITIATED_SESSION_ID_1)))
                .suspendsWith(
                    FlowIORequest.SubFlowFinished(listOf(INITIATED_SESSION_ID_1))
                )
                .completedSuccessfullyWith("hello")
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                scheduleFlowMapperCleanupEvents(INITIATED_SESSION_ID_1)
            }
        }
    }

    @Test
    fun `Given an initiated top level flow with an errored session when it finishes and calls SubFlowFinished, cleanup is scheduled`() {
        given {
            membershipGroupFor(BOB_HOLDING_IDENTITY)
            initiatingToInitiatedFlow(PROTOCOL_2, FLOW_NAME, FLOW_NAME_2)

            sessionInitEventReceived(FLOW_ID1, INITIATED_SESSION_ID_1, CPI1, PROTOCOL_2)
                .suspendsWith(FlowIORequest.Receive(setOf(SessionInfo(INITIATED_SESSION_ID_1, initiatingIdentityMemberName))))
        }

        `when` {
            sessionErrorEventReceived(FLOW_ID1, INITIATED_SESSION_ID_1)
                .suspendsWith(
                    FlowIORequest.SubFlowFinished(listOf(INITIATED_SESSION_ID_1))
                )
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                scheduleFlowMapperCleanupEvents(INITIATED_SESSION_ID_1)
            }
        }
    }
}