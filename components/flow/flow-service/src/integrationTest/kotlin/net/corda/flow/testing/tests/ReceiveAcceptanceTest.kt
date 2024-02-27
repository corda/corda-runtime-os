package net.corda.flow.testing.tests

import net.corda.flow.application.sessions.SessionInfo
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.testing.context.FlowServiceTestBase
import net.corda.flow.testing.context.flowResumedWithError
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
class ReceiveAcceptanceTest : FlowServiceTestBase() {

    private companion object {

        val DATA_MESSAGE_3 = byteArrayOf(3)
        val DATA_MESSAGE_4 = byteArrayOf(4)
        val DATA_MESSAGE_5 = byteArrayOf(5)
        val DATA_MESSAGE_6 = byteArrayOf(6)
    }

    @BeforeEach
    fun beforeEach() {
        given {
            virtualNode(CPI1, ALICE_HOLDING_IDENTITY)
            cpkMetadata(CPI1, CPK1, CPK1_CHECKSUM)
            sandboxCpk(CPK1_CHECKSUM)
            membershipGroupFor(ALICE_HOLDING_IDENTITY)

            sessionInitiatingIdentity(ALICE_HOLDING_IDENTITY)
            sessionInitiatedIdentity(BOB_HOLDING_IDENTITY)

            initiatingToInitiatedFlow(PROTOCOL, FAKE_FLOW_NAME, FAKE_FLOW_NAME)
        }
    }

    @Test
    fun `Receiving an out-of-order session data events does not resume the flow and sends a session ack`() {
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

        }

        `when` {
            sessionDataEventReceived(FLOW_ID1, SESSION_ID_1, DATA_MESSAGE_1, sequenceNum = -1)
            sessionDataEventReceived(FLOW_ID1, SESSION_ID_1, DATA_MESSAGE_1, sequenceNum = 5)
            sessionDataEventReceived(FLOW_ID1, SESSION_ID_1, DATA_MESSAGE_1, sequenceNum = 3)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
                noOutputEvent()
            }

            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
                noOutputEvent()
            }

            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
                noOutputEvent()
            }
        }
    }

    @Test
    fun `Receiving a session close event instead of a data resumes the flow with an error`() {
        given {
            startFlow(this)
                .suspendsWith(
                    FlowIORequest.Receive(
                        setOf(
                            SessionInfo(SESSION_ID_1, initiatedIdentityMemberName),
                        )
                    )
                )
        }

        `when` {
            sessionCloseEventReceived(FLOW_ID1, SESSION_ID_1, sequenceNum = 1)
                .completedWithError(CordaRuntimeException("error"))
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowResumedWithError<CordaRuntimeException>()
            }
        }
    }

    @Test
    fun `Given two sessions receiving a single session data event does not resume the flow and sends a session ack`() {
        given {
            startFlow(this)
                .suspendsWith(FlowIORequest.Receive(setOf(
                    SessionInfo(SESSION_ID_1, initiatedIdentityMemberName),
                    SessionInfo(SESSION_ID_2, initiatedIdentityMemberName),
                )))
        }

        `when` {
            sessionDataEventReceived(FLOW_ID1, SESSION_ID_1, DATA_MESSAGE_1, sequenceNum = 1)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
                sessionAckEvents(SESSION_ID_1)
            }
        }
    }

    @Test
    fun `Given two sessions receiving all session data events resumes the flow`() {
        given {
            startFlow(this)
                .suspendsWith(FlowIORequest.Receive(setOf(
                    SessionInfo(SESSION_ID_1, initiatedIdentityMemberName),
                    SessionInfo(SESSION_ID_2, initiatedIdentityMemberName),
                )))
        }

        `when` {
            sessionDataEventReceived(FLOW_ID1, SESSION_ID_1, DATA_MESSAGE_1, sequenceNum = 1)

            sessionDataEventReceived(FLOW_ID1, SESSION_ID_2, DATA_MESSAGE_2, sequenceNum = 1)
                .suspendsWith(FlowIORequest.Receive(setOf(
                    SessionInfo(SESSION_ID_1, initiatedIdentityMemberName),
                )))
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
                noOutputEvent()
            }

            expectOutputForFlow(FLOW_ID1) {
                flowResumedWithData(mapOf(SESSION_ID_1 to DATA_MESSAGE_1, SESSION_ID_2 to DATA_MESSAGE_2))
                noOutputEvent()
            }
        }
    }

    @Suppress("MaxLineLength")
    @Test
    fun `Given two sessions where one has already received a session data event calling 'receive' and then receiving a session data event for the other session resumes the flow `() {
        given {
            startFlow(this)
                .suspendsWith(FlowIORequest.Receive(setOf(
                    SessionInfo(SESSION_ID_1, initiatedIdentityMemberName),
                    SessionInfo(SESSION_ID_2, initiatedIdentityMemberName),
                )))

            sessionDataEventReceived(FLOW_ID1, SESSION_ID_1, DATA_MESSAGE_1, sequenceNum = 1)

        }

        `when` {
            sessionDataEventReceived(FLOW_ID1, SESSION_ID_2, DATA_MESSAGE_2, sequenceNum = 1)
                .suspendsWith(FlowIORequest.Receive(setOf(
                    SessionInfo(SESSION_ID_1, initiatedIdentityMemberName),
                )))
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowResumedWithData(mapOf(SESSION_ID_1 to DATA_MESSAGE_1, SESSION_ID_2 to DATA_MESSAGE_2))
                noOutputEvent()
            }
        }
    }

    @Suppress("MaxLineLength")
    @Test
    fun `Given two sessions have already received their session data events when the flow calls 'receive' for each session individually the flow should resume`() {
        given {
            startFlow(this)
                .suspendsWith(FlowIORequest.Receive(setOf(
                    SessionInfo(SESSION_ID_1, initiatedIdentityMemberName),
                )))

            sessionDataEventReceived(FLOW_ID1, SESSION_ID_1, DATA_MESSAGE_2, sequenceNum = 2)
            sessionDataEventReceived(FLOW_ID1, SESSION_ID_2, DATA_MESSAGE_1, sequenceNum = 1)
        }

        `when` {
            sessionDataEventReceived(FLOW_ID1, SESSION_ID_1, DATA_MESSAGE_1, sequenceNum = 1)
                .suspendsWith(FlowIORequest.Receive(setOf(
                    SessionInfo(SESSION_ID_2, initiatedIdentityMemberName),
                )))
                .suspendsWith(FlowIORequest.Receive(setOf(
                    SessionInfo(SESSION_ID_1, initiatedIdentityMemberName),
                )))
                .suspendsWith(FlowIORequest.Receive(setOf(
                    SessionInfo(SESSION_ID_1, initiatedIdentityMemberName),
                )))

        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowResumedWithData(mapOf(SESSION_ID_1 to DATA_MESSAGE_2))
                noOutputEvent()
            }
        }
    }

    @Test
    fun `Given two sessions receiving a single session error event does not resume the flow and schedules session cleanup`() {
        given {
            startFlow(this)
                .suspendsWith(FlowIORequest.Receive(setOf(
                    SessionInfo(SESSION_ID_1, initiatedIdentityMemberName),
                    SessionInfo(SESSION_ID_2, initiatedIdentityMemberName),
                )))
        }

        `when` {
            sessionErrorEventReceived(FLOW_ID1, SESSION_ID_1)
                .suspendsWith(FlowIORequest.ForceCheckpoint)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
                scheduleFlowMapperCleanupEvents(SESSION_ID_1)
            }
        }
    }

    @Suppress("MaxLineLength")
    @Test
    fun `Given two sessions receiving a session data event for one and a session error event for the other resumes the flow with an error and schedules session cleanup`() {
        given {
            startFlow(this)
                .suspendsWith(FlowIORequest.Receive(setOf(
                    SessionInfo(SESSION_ID_1, initiatedIdentityMemberName),
                    SessionInfo(SESSION_ID_2, initiatedIdentityMemberName),
                )))
        }

        `when` {
            sessionDataEventReceived(FLOW_ID1, SESSION_ID_1, DATA_MESSAGE_1, sequenceNum = 1)
            sessionErrorEventReceived(FLOW_ID1, SESSION_ID_2)
                .suspendsWith(FlowIORequest.Receive(setOf(
                    SessionInfo(SESSION_ID_1, initiatedIdentityMemberName),
                )))
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
                noOutputEvent()
            }

            expectOutputForFlow(FLOW_ID1) {
                flowResumedWithError<CordaRuntimeException>()
                scheduleFlowMapperCleanupEvents(SESSION_ID_2)
            }
        }
    }

    @Suppress("MaxLineLength")
    @Test
    fun `Given two sessions receiving a session error event first for one and a session data event for the other resumes the flow with an error and schedules session cleanup`() {
        given {
            startFlow(this)
                .suspendsWith(FlowIORequest.Receive(setOf(
                    SessionInfo(SESSION_ID_1, initiatedIdentityMemberName),
                    SessionInfo(SESSION_ID_2, initiatedIdentityMemberName),
                )))
        }

        `when` {
            sessionErrorEventReceived(FLOW_ID1, SESSION_ID_2)
            sessionDataEventReceived(FLOW_ID1, SESSION_ID_1, DATA_MESSAGE_1, sequenceNum = 1)
                .suspendsWith(FlowIORequest.FlowFailed(CordaRuntimeException("error")))
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
                scheduleFlowMapperCleanupEvents(SESSION_ID_2)
            }

            expectOutputForFlow(FLOW_ID1) {
                flowResumedWithError<CordaRuntimeException>()
                noOutputEvent()
            }
        }
    }

    @Suppress("MaxLineLength")
    @Test
    fun `Given two sessions receiving a session close event for one session and a session data event for the other resumes the flow with an error`() {
        given {
            startFlow(this)
                .suspendsWith(FlowIORequest.Receive(setOf(
                    SessionInfo(SESSION_ID_1, initiatedIdentityMemberName),
                    SessionInfo(SESSION_ID_2, initiatedIdentityMemberName),
                )))
        }

        `when` {
            sessionDataEventReceived(FLOW_ID1, SESSION_ID_1, DATA_MESSAGE_1, sequenceNum = 1)
            sessionCloseEventReceived(FLOW_ID1, SESSION_ID_2, sequenceNum = 1)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
            }

            expectOutputForFlow(FLOW_ID1) {
                flowResumedWithError<CordaRuntimeException>()
                noOutputEvent()
            }
        }
    }

    @Test
    fun `Given two sessions receiving session close events for both sessions resumes the flow with an error`() {
        given {
            startFlow(this)
                .suspendsWith(FlowIORequest.Receive(setOf(
                    SessionInfo(SESSION_ID_1, initiatedIdentityMemberName),
                    SessionInfo(SESSION_ID_2, initiatedIdentityMemberName),
                )))
        }

        `when` {
            sessionCloseEventReceived(FLOW_ID1, SESSION_ID_1, sequenceNum = 1)
            sessionCloseEventReceived(FLOW_ID1, SESSION_ID_2, sequenceNum = 1)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
            }

            expectOutputForFlow(FLOW_ID1) {
                flowResumedWithError<CordaRuntimeException>()
            }
        }
    }

    @Suppress("MaxLineLength")
    @Test
    fun `Given two sessions receiving a session data and then close event for one session and a session data event for the other resumes the flow`() {
        given {
            startFlow(this)
                .suspendsWith(FlowIORequest.Receive(setOf(
                    SessionInfo(SESSION_ID_1, initiatedIdentityMemberName),
                    SessionInfo(SESSION_ID_2, initiatedIdentityMemberName),
                )))
        }

        `when` {
            sessionDataEventReceived(FLOW_ID1, SESSION_ID_1, DATA_MESSAGE_1, sequenceNum = 1)
            sessionCloseEventReceived(FLOW_ID1, SESSION_ID_1, sequenceNum = 2)
            sessionDataEventReceived(FLOW_ID1, SESSION_ID_2, DATA_MESSAGE_2, sequenceNum = 1)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
                noOutputEvent()
            }

            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
                noOutputEvent()
            }

            expectOutputForFlow(FLOW_ID1) {
                flowResumedWithData(mapOf(SESSION_ID_1 to DATA_MESSAGE_1, SESSION_ID_2 to DATA_MESSAGE_2))
                noOutputEvent()
            }
        }
    }

    @Test
    fun `Given a session, if it receives an out of order close and then an ordered data event, the flow resumes`() {
        given {
            startFlow(this)
                .suspendsWith(FlowIORequest.Receive(setOf(
                    SessionInfo(SESSION_ID_1, initiatedIdentityMemberName),
                    SessionInfo(SESSION_ID_1, initiatedIdentityMemberName),
                )))
        }

        `when` {
            sessionCloseEventReceived(FLOW_ID1, SESSION_ID_1, sequenceNum = 2)
            sessionDataEventReceived(FLOW_ID1, SESSION_ID_1, DATA_MESSAGE_1, sequenceNum = 1)
                .completedSuccessfullyWith("hello")
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
                noOutputEvent()
            }

            expectOutputForFlow(FLOW_ID1) {
                flowResumedWithData(mapOf(SESSION_ID_1 to DATA_MESSAGE_1))
                noOutputEvent()
            }
        }
    }

    @Test
    fun `Complex messaging flow executing multiple sends and receives with 2 sessions, receives arrive non-sequentially`() {
        given {
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
                .suspendsWith(FlowIORequest.Send(mapOf(
                    SessionInfo(SESSION_ID_1, initiatedIdentityMemberName) to DATA_MESSAGE_0,
                    SessionInfo(SESSION_ID_2, initiatedIdentityMemberName) to DATA_MESSAGE_0),
                ))
                .suspendsWith(FlowIORequest.Send(
                    mapOf(
                        SessionInfo(SESSION_ID_1, initiatedIdentityMemberName) to  DATA_MESSAGE_1,
                        SessionInfo(SESSION_ID_2, initiatedIdentityMemberName) to  DATA_MESSAGE_1,
                    )))
                .suspendsWith(FlowIORequest.Send(
                    mapOf(
                        SessionInfo(SESSION_ID_1, initiatedIdentityMemberName) to  DATA_MESSAGE_2,
                    )))
                .suspendsWith(FlowIORequest.Receive(
                    setOf(
                        SessionInfo(SESSION_ID_1, initiatedIdentityMemberName),
                        SessionInfo(SESSION_ID_2, initiatedIdentityMemberName),
                    )))

            sessionDataEventReceived(FLOW_ID1, SESSION_ID_1, DATA_MESSAGE_3, 1)

            sessionDataEventReceived(FLOW_ID1, SESSION_ID_2, DATA_MESSAGE_3, 1)
                .suspendsWith(FlowIORequest.Receive(
                    setOf(
                        SessionInfo(SESSION_ID_1, initiatedIdentityMemberName),
                        SessionInfo(SESSION_ID_2, initiatedIdentityMemberName),
                    )))

            sessionDataEventReceived(FLOW_ID1, SESSION_ID_1, DATA_MESSAGE_4, 2)
            sessionDataEventReceived(FLOW_ID1, SESSION_ID_1, DATA_MESSAGE_5, 3)
            sessionDataEventReceived(FLOW_ID1, SESSION_ID_1, DATA_MESSAGE_6, 4)
        }

        `when` {
            sessionDataEventReceived(FLOW_ID1, SESSION_ID_2, DATA_MESSAGE_4, 2)
                .suspendsWith(FlowIORequest.Receive(
                    setOf(
                        SessionInfo(SESSION_ID_1, initiatedIdentityMemberName)
                    )))
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowResumedWithData(mapOf(SESSION_ID_1 to DATA_MESSAGE_5))
            }
        }
    }
}