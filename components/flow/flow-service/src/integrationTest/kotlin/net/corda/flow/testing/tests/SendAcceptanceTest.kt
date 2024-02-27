package net.corda.flow.testing.tests

import net.corda.flow.application.sessions.SessionInfo
import net.corda.flow.fiber.FlowIORequest
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
class SendAcceptanceTest : FlowServiceTestBase() {

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
    fun `Calling 'send' multiple times on initiated sessions sends multiple session data events`() {
        given {
            startFlow(this)
                .suspendsWith(FlowIORequest.Receive(setOf(SessionInfo(SESSION_ID_1, initiatedIdentityMemberName))))
        }

        `when` {
            sessionDataEventReceived(FLOW_ID1, SESSION_ID_1, DATA_MESSAGE_1, sequenceNum = 1)
                .suspendsWith(FlowIORequest.Send(
                    mapOf(
                        SessionInfo(SESSION_ID_1, initiatedIdentityMemberName) to  DATA_MESSAGE_1,
                    )))
                .suspendsWith(FlowIORequest.Send(
                    mapOf(
                        SessionInfo(SESSION_ID_1, initiatedIdentityMemberName) to  DATA_MESSAGE_2,
                    )))
                .suspendsWith(FlowIORequest.Receive(setOf(SessionInfo(SESSION_ID_1, initiatedIdentityMemberName))))
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                multipleSessionDataEvents(mapOf(SESSION_ID_1 to listOf(DATA_MESSAGE_1, DATA_MESSAGE_2)))
            }
        }
    }

    @Test
    fun `Calling 'send' on an invalid session fails and reports the exception to user code`() {
        given {
            startFlow(this)
                .suspendsWith(FlowIORequest.Receive(setOf(SessionInfo(SESSION_ID_1, initiatedIdentityMemberName))))
        }

        `when` {
            sessionCloseEventReceived(FLOW_ID1, SESSION_ID_1, sequenceNum = 2)

            sessionDataEventReceived(FLOW_ID1, SESSION_ID_1, DATA_MESSAGE_1, sequenceNum = 1)
                .suspendsWith(FlowIORequest.Send(
                    mapOf(
                        SessionInfo(SESSION_ID_1, initiatedIdentityMemberName) to  DATA_MESSAGE_1,
                    )))

        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                noOutputEvent()
            }

            expectOutputForFlow(FLOW_ID1) {
                flowResumedWithError(CordaRuntimeException::class.java)
            }
        }
    }

    @Test
    fun `Calling 'send' multiple times on initiated sessions resumes the flow and sends a session data events each time`() {
        given {
            startFlow(this)
                .suspendsWith(FlowIORequest.Receive(setOf(SessionInfo(SESSION_ID_1, initiatedIdentityMemberName))))
        }

        `when` {
            sessionDataEventReceived(FLOW_ID1, SESSION_ID_1, DATA_MESSAGE_1, 1)
                .suspendsWith(FlowIORequest.Send(
                    mapOf(
                        SessionInfo(SESSION_ID_1, initiatedIdentityMemberName) to  DATA_MESSAGE_1,
                        SessionInfo(SESSION_ID_2, initiatedIdentityMemberName) to  DATA_MESSAGE_2,
                    )))
                .suspendsWith(FlowIORequest.Send(
                    mapOf(
                        SessionInfo(SESSION_ID_1, initiatedIdentityMemberName) to  DATA_MESSAGE_3,
                        SessionInfo(SESSION_ID_2, initiatedIdentityMemberName) to  DATA_MESSAGE_4,
                    )))
                .suspendsWith(FlowIORequest.Send(
                    mapOf(
                        SessionInfo(SESSION_ID_1, initiatedIdentityMemberName) to  DATA_MESSAGE_5,
                        SessionInfo(SESSION_ID_2, initiatedIdentityMemberName) to  DATA_MESSAGE_6,
                    )))
                .suspendsWith(FlowIORequest.Receive(setOf(SessionInfo(SESSION_ID_1, initiatedIdentityMemberName))))

        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                sessionDataEvents(SESSION_ID_1 to DATA_MESSAGE_1, SESSION_ID_2 to DATA_MESSAGE_2, SESSION_ID_1 to DATA_MESSAGE_3,
                    SESSION_ID_2 to DATA_MESSAGE_4, SESSION_ID_1 to DATA_MESSAGE_5, SESSION_ID_2 to DATA_MESSAGE_6)
                noOutputEvent()
            }
        }
    }

    @Suppress("MaxLineLength")
    @Test
    fun `Given a flow resumes after receiving session data events calling 'send' on the sessions sends session data events and no session ack for the session that resumed the flow`() {
        given {
            startFlow(this)
                .suspendsWith(FlowIORequest.Receive(setOf(
                    SessionInfo(SESSION_ID_1, initiatedIdentityMemberName),
                    SessionInfo(SESSION_ID_2, initiatedIdentityMemberName)
                )))
        }

        `when` {
            sessionDataEventReceived(FLOW_ID1, SESSION_ID_1, DATA_MESSAGE_1, sequenceNum = 1)

            sessionDataEventReceived(FLOW_ID1, SESSION_ID_2, DATA_MESSAGE_2, sequenceNum = 1)
                .suspendsWith(FlowIORequest.Send(
                    mapOf(
                        SessionInfo(SESSION_ID_1, initiatedIdentityMemberName) to  DATA_MESSAGE_3,
                        SessionInfo(SESSION_ID_2, initiatedIdentityMemberName) to  DATA_MESSAGE_4,
                    )))
                .suspendsWith(FlowIORequest.Receive(setOf(
                    SessionInfo(SESSION_ID_1, initiatedIdentityMemberName)
                )))
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
                noOutputEvent()
            }

            expectOutputForFlow(FLOW_ID1) {
                flowResumedWithData(mapOf(SESSION_ID_1 to DATA_MESSAGE_1, SESSION_ID_2 to DATA_MESSAGE_2))
                sessionDataEvents(SESSION_ID_1 to DATA_MESSAGE_3, SESSION_ID_2 to DATA_MESSAGE_4)
            }
        }
    }
}