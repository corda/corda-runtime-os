package net.corda.flow.testing.tests

import net.corda.data.flow.output.FlowStates
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
class InitiateFlowAcceptanceTest : FlowServiceTestBase() {

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
    fun `Requesting counterparty info flow sends a session init event`() {
        `when` {
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
                .suspendsWith(FlowIORequest.CounterPartyFlowInfo(SessionInfo(SESSION_ID_1, initiatedIdentityMemberName)))
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                sessionInitEvents(SESSION_ID_1)
            }
        }
    }

    @Test
    fun `Requesting counterparty info from the flow engine that has already sent a session init event does not send another SessionInit`() {
        given {
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
                .suspendsWith(FlowIORequest.Send(mapOf(SessionInfo(SESSION_ID_1, initiatedIdentityMemberName) to DATA_MESSAGE_0)))
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                noFlowEvents()
            }
        }
    }

    @Test
    fun `Receiving a session init event starts an initiated flow and sends a session confirm`() {
        given {
            virtualNode(CPI1, BOB_HOLDING_IDENTITY)
            membershipGroupFor(BOB_HOLDING_IDENTITY)
            initiatingToInitiatedFlow(PROTOCOL, FAKE_FLOW_NAME, FAKE_FLOW_NAME)
        }

        `when` {
            sessionInitEventReceived(FLOW_ID1, INITIATED_SESSION_ID_1, CPI1, PROTOCOL)
                .suspendsWith(FlowIORequest.InitialCheckpoint)
                .suspendsWith(
                    FlowIORequest.Receive(
                        setOf(
                            SessionInfo(INITIATED_SESSION_ID_1, initiatingIdentityMemberName),
                        )
                    )
                )
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowStatus(FlowStates.RUNNING)
                sessionConfirmEvents(INITIATED_SESSION_ID_1)
                flowFiberCacheContainsKey(BOB_HOLDING_IDENTITY, INITIATED_SESSION_ID_1)
                flowResumedWith(Unit)
            }
        }
    }

    @Test
    fun `Receiving a session error event resumes the flow with an error`() {
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
            sessionErrorEventReceived(FLOW_ID1, SESSION_ID_1)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowResumedWithError<CordaRuntimeException>()
            }
        }
    }
}