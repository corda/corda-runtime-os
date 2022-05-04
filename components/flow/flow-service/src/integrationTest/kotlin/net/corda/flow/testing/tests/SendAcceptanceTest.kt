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
class SendAcceptanceTest : FlowServiceTestBase() {

    private companion object {
        val DATA_MESSAGE_3 = byteArrayOf(3)
        val DATA_MESSAGE_4 = byteArrayOf(4)
        val DATA_MESSAGE_5 = byteArrayOf(5)
        val DATA_MESSAGE_6 = byteArrayOf(6)
    }

    @Test
    fun `(Send) Calling 'send' on initiated sessions sends a session data event and schedules a wakeup event`() {
        given {
            virtualNode(CPI1, ALICE_HOLDING_IDENTITY)
            cpkMetadata(CPI1, CPK1)
            sandboxCpk(CPK1)
            membershipGroupFor(ALICE_HOLDING_IDENTITY)

            sessionInitiatingIdentity(ALICE_HOLDING_IDENTITY)
            sessionInitiatedIdentity(BOB_HOLDING_IDENTITY)

            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
                .suspendsWith(FlowIORequest.InitiateFlow(initiatedIdentityMemberName, SESSION_ID_1))

            sessionAckEventReceived(FLOW_ID1, SESSION_ID_1, 1)
                .suspendsWith(FlowIORequest.InitiateFlow(initiatedIdentityMemberName, SESSION_ID_2))
        }

        `when` {
            sessionAckEventReceived(FLOW_ID1, SESSION_ID_2, 1)
                .suspendsWith(FlowIORequest.Send(mapOf(SESSION_ID_1 to DATA_MESSAGE_1, SESSION_ID_2 to DATA_MESSAGE_2)))
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                sessionDataEvent(SESSION_ID_1, DATA_MESSAGE_1)
                sessionDataEvent(SESSION_ID_2, DATA_MESSAGE_2)
                wakeUpEvent()
            }
        }
    }

    @Test
    fun `(Send) Calling 'send' multiple times on initiated sessions resumes the flow and sends a session data events each time`() {
        given {
            virtualNode(CPI1, ALICE_HOLDING_IDENTITY)
            cpkMetadata(CPI1, CPK1)
            sandboxCpk(CPK1)
            membershipGroupFor(ALICE_HOLDING_IDENTITY)

            sessionInitiatingIdentity(ALICE_HOLDING_IDENTITY)
            sessionInitiatedIdentity(BOB_HOLDING_IDENTITY)

            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
                .suspendsWith(FlowIORequest.InitiateFlow(initiatedIdentityMemberName, SESSION_ID_1))

            sessionAckEventReceived(FLOW_ID1, SESSION_ID_1, 1)
                .suspendsWith(FlowIORequest.InitiateFlow(initiatedIdentityMemberName, SESSION_ID_2))
        }

        `when` {
            sessionAckEventReceived(FLOW_ID1, SESSION_ID_2, 1)
                .suspendsWith(FlowIORequest.Send(mapOf(SESSION_ID_1 to DATA_MESSAGE_1, SESSION_ID_2 to DATA_MESSAGE_2)))

            wakeupEventReceived(FLOW_ID1)
                .suspendsWith(FlowIORequest.Send(mapOf(SESSION_ID_1 to DATA_MESSAGE_3, SESSION_ID_2 to DATA_MESSAGE_4)))

            wakeupEventReceived(FLOW_ID1)
                .suspendsWith(FlowIORequest.Send(mapOf(SESSION_ID_1 to DATA_MESSAGE_5, SESSION_ID_2 to DATA_MESSAGE_6)))
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                sessionDataEvent(SESSION_ID_1, DATA_MESSAGE_1)
                sessionDataEvent(SESSION_ID_2, DATA_MESSAGE_2)
                wakeUpEvent()
            }

            expectOutputForFlow(FLOW_ID1) {
                flowResumedWith(Unit)
                sessionDataEvent(SESSION_ID_1, DATA_MESSAGE_3)
                sessionDataEvent(SESSION_ID_2, DATA_MESSAGE_4)
                wakeUpEvent()
            }

            expectOutputForFlow(FLOW_ID1) {
                flowResumedWith(Unit)
                sessionDataEvent(SESSION_ID_1, DATA_MESSAGE_5)
                sessionDataEvent(SESSION_ID_2, DATA_MESSAGE_6)
                wakeUpEvent()
            }
        }
    }
}