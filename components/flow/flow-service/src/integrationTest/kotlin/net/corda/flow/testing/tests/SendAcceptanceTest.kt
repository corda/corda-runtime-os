package net.corda.flow.testing.tests

import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.testing.context.FlowServiceTestBase
import net.corda.flow.testing.context.flowResumedWithError
import net.corda.flow.testing.context.initiateFlowMessageFor
import net.corda.v5.application.flows.exceptions.FlowException
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
    fun `Calling 'send' on initiated sessions sends a session data event and schedules a wakeup event`() {
        given {
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
                .suspendsWith(initiateFlowMessageFor(initiatedIdentityMemberName, SESSION_ID_1))

            sessionAckEventReceived(FLOW_ID1, SESSION_ID_1, receivedSequenceNum = 1)
                .suspendsWith(initiateFlowMessageFor(initiatedIdentityMemberName, SESSION_ID_2))
        }

        `when` {
            sessionAckEventReceived(FLOW_ID1, SESSION_ID_2, receivedSequenceNum = 1)
                .suspendsWith(FlowIORequest.Send(mapOf(SESSION_ID_1 to DATA_MESSAGE_1, SESSION_ID_2 to DATA_MESSAGE_2)))
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                sessionDataEvents(SESSION_ID_1 to DATA_MESSAGE_1, SESSION_ID_2 to DATA_MESSAGE_2)
                wakeUpEvent()
            }
        }
    }

    @Test
    fun `Calling 'send' on an invalid session fails and reports the exception to user code`() {
        given {
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
                .suspendsWith(initiateFlowMessageFor(initiatedIdentityMemberName, SESSION_ID_1))

            sessionAckEventReceived(FLOW_ID1, SESSION_ID_1, receivedSequenceNum = 1)
                .suspendsWith(FlowIORequest.ForceCheckpoint)
        }

        `when` {
            sessionCloseEventReceived(FLOW_ID1, SESSION_ID_1, sequenceNum = 1, receivedSequenceNum = 1)
                .suspendsWith(FlowIORequest.Send(mapOf(SESSION_ID_1 to DATA_MESSAGE_1)))
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                hasPendingUserException()
                wakeUpEvent()
            }
        }

        `when` {
            wakeupEventReceived(FLOW_ID1)
                .suspendsWith(FlowIORequest.FlowFailed(Exception()))
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowResumedWithError<FlowException>()
                noPendingUserException()
            }
        }
    }

    @Test
    fun `Calling 'send' multiple times on initiated sessions resumes the flow and sends a session data events each time`() {
        given {
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
                .suspendsWith(initiateFlowMessageFor(initiatedIdentityMemberName, SESSION_ID_1))

            sessionAckEventReceived(FLOW_ID1, SESSION_ID_1, receivedSequenceNum = 1)
                .suspendsWith(initiateFlowMessageFor(initiatedIdentityMemberName, SESSION_ID_2))
        }

        `when` {
            sessionAckEventReceived(FLOW_ID1, SESSION_ID_2, receivedSequenceNum = 1)
                .suspendsWith(FlowIORequest.Send(mapOf(SESSION_ID_1 to DATA_MESSAGE_1, SESSION_ID_2 to DATA_MESSAGE_2)))

            wakeupEventReceived(FLOW_ID1)
                .suspendsWith(FlowIORequest.Send(mapOf(SESSION_ID_1 to DATA_MESSAGE_3, SESSION_ID_2 to DATA_MESSAGE_4)))

            wakeupEventReceived(FLOW_ID1)
                .suspendsWith(FlowIORequest.Send(mapOf(SESSION_ID_1 to DATA_MESSAGE_5, SESSION_ID_2 to DATA_MESSAGE_6)))
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                sessionDataEvents(SESSION_ID_1 to DATA_MESSAGE_1, SESSION_ID_2 to DATA_MESSAGE_2)
                wakeUpEvent()
            }

            expectOutputForFlow(FLOW_ID1) {
                flowResumedWith(Unit)
                sessionDataEvents(SESSION_ID_1 to DATA_MESSAGE_3, SESSION_ID_2 to DATA_MESSAGE_4)
                wakeUpEvent()
            }

            expectOutputForFlow(FLOW_ID1) {
                flowResumedWith(Unit)
                sessionDataEvents(SESSION_ID_1 to DATA_MESSAGE_5, SESSION_ID_2 to DATA_MESSAGE_6)
                wakeUpEvent()
            }
        }
    }

    @Test
    fun `Given a flow resumes after receiving session data events calling 'send' on the sessions sends session data events and no session ack for the session that resumed the flow`() {
        given {
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
                .suspendsWith(initiateFlowMessageFor(initiatedIdentityMemberName, SESSION_ID_1))

            sessionAckEventReceived(FLOW_ID1, SESSION_ID_1, receivedSequenceNum = 1)
                .suspendsWith(initiateFlowMessageFor(initiatedIdentityMemberName, SESSION_ID_2))

            sessionAckEventReceived(FLOW_ID1, SESSION_ID_2, receivedSequenceNum = 1)
                .suspendsWith(FlowIORequest.Receive(setOf(SESSION_ID_1, SESSION_ID_2)))
        }

        `when` {
            sessionDataEventReceived(FLOW_ID1, SESSION_ID_1, DATA_MESSAGE_1, sequenceNum = 1, receivedSequenceNum = 1)

            sessionDataEventReceived(FLOW_ID1, SESSION_ID_2, DATA_MESSAGE_2, sequenceNum = 1, receivedSequenceNum = 1)
                .suspendsWith(FlowIORequest.Send(mapOf(SESSION_ID_1 to DATA_MESSAGE_3, SESSION_ID_2 to DATA_MESSAGE_4)))
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