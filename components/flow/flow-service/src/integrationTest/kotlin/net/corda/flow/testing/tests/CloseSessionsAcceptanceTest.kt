package net.corda.flow.testing.tests

import net.corda.flow.application.sessions.SessionInfo
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.testing.context.ALICE_FLOW_KEY
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
class CloseSessionsAcceptanceTest : FlowServiceTestBase() {

    @BeforeEach
    fun beforeEach() {
        given {
            virtualNode(CPI1, ALICE_HOLDING_IDENTITY)
            virtualNode(CPI1, BOB_HOLDING_IDENTITY)
            cpkMetadata(CPI1, CPK1, CPK1_CHECKSUM)
            sandboxCpk(CPK1_CHECKSUM)
            membershipGroupFor(ALICE_HOLDING_IDENTITY)

            sessionInitiatingIdentity(ALICE_HOLDING_IDENTITY)
            sessionInitiatedIdentity(BOB_HOLDING_IDENTITY)

            initiatingToInitiatedFlow(PROTOCOL, FAKE_FLOW_NAME, FAKE_FLOW_NAME)
        }
    }

    @Test
    fun `Clling 'close' on a session completes successfully`() {
        given {
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
                .suspendsWith(FlowIORequest.Receive(setOf(SessionInfo(SESSION_ID_1, initiatedIdentityMemberName))))

            sessionCloseEventReceived(FLOW_ID1, SESSION_ID_1, sequenceNum = 2)
        }

        `when` {
            sessionDataEventReceived(FLOW_ID1, SESSION_ID_1, DATA_MESSAGE_1, 1)
                .suspendsWith(FlowIORequest.CloseSessions(setOf(SESSION_ID_1)))
                .completedSuccessfullyWith("hello")
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                scheduleFlowMapperCleanupEvents(ALICE_FLOW_KEY, SESSION_ID_1)
            }
        }
    }

    @Test
    fun `Receiving an out-of-order session close events does not resume the flow`() {
        given {
            startFlow(this)
                .suspendsWith(FlowIORequest.Receive(setOf(SessionInfo(SESSION_ID_1, initiatedIdentityMemberName))))

        }

        `when` {
            sessionCloseEventReceived(FLOW_ID1, SESSION_ID_1, sequenceNum = 2)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
            }
        }
    }

    @Test
    fun `Receiving an ordered session close event when waiting to receive data errors the flow`() {
        given {
            startFlow(this)
                .suspendsWith(FlowIORequest.Receive(setOf(SessionInfo(SESSION_ID_1, initiatedIdentityMemberName))))
        }

        `when` {
            sessionCloseEventReceived(FLOW_ID1, SESSION_ID_1, sequenceNum = 1)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowResumedWithError(CordaRuntimeException::class.java)
            }
        }
    }
}
