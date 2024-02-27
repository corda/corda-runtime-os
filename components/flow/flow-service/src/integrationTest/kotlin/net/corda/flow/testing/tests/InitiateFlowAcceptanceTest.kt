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
    fun `Requesting counterparty info flow sends a CounterpartyInfoRequest event`() {
        `when` {
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
                .suspendsWith(FlowIORequest.CounterPartyFlowInfo(SessionInfo(SESSION_ID_1, initiatedIdentityMemberName)))
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                sessionCounterpartyInfoRequestEvents(SESSION_ID_1)
            }
        }
    }

    @Suppress("MaxLineLength")
    @Test
    fun `Requesting counterparty info from the flow engine that has already sent a CounterpartyInfoRequest event does not send another SessionInit`() {
        given {
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
                .suspendsWith(FlowIORequest.CounterPartyFlowInfo(SessionInfo(SESSION_ID_1, initiatedIdentityMemberName)))
        }

        `when` {
            sessionCounterpartyInfoResponseReceived(FLOW_ID1, SESSION_ID_1)
                .suspendsWith(FlowIORequest.CounterPartyFlowInfo(SessionInfo(SESSION_ID_1, initiatedIdentityMemberName)))
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                noFlowEvents()
            }
        }
    }

    @Test
    fun `Receiving a CounterpartyInfoRequest event starts an initiated flow and sends a session confirm`() {
        given {
            virtualNode(CPI1, BOB_HOLDING_IDENTITY)
            membershipGroupFor(BOB_HOLDING_IDENTITY)
            initiatingToInitiatedFlow(PROTOCOL, FAKE_FLOW_NAME, FAKE_FLOW_NAME)
        }

        `when` {
            sessionCounterpartyInfoRequestReceived(
                FLOW_ID1, INITIATED_SESSION_ID_1, CPI1, PROTOCOL, ALICE_HOLDING_IDENTITY,
                BOB_HOLDING_IDENTITY, true)
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
                sessionCounterpartyInfoResponse(INITIATED_SESSION_ID_1)
                flowFiberCacheContainsKey(BOB_HOLDING_IDENTITY, INITIATED_SESSION_ID_1)
                flowResumedWith(Unit)
            }
        }
    }

    @Test
    fun `Receiving 2 out of order SessionData events starts an initiated flow and processes both datas in order`() {
        given {
            virtualNode(CPI1, BOB_HOLDING_IDENTITY)
            membershipGroupFor(BOB_HOLDING_IDENTITY)
            initiatingToInitiatedFlow(PROTOCOL, FAKE_FLOW_NAME, FAKE_FLOW_NAME)
        }

        `when` {
            sessionDataEventReceived(FLOW_ID1, INITIATED_SESSION_ID_1, DATA_MESSAGE_2, 2, SESSION_INIT)
                .suspendsWith(FlowIORequest.InitialCheckpoint)
                .suspendsWith(
                    FlowIORequest.Receive(
                        setOf(
                            SessionInfo(INITIATED_SESSION_ID_1, initiatingIdentityMemberName),
                        )
                    )
                )

            sessionDataEventReceived(FLOW_ID1, INITIATED_SESSION_ID_1, DATA_MESSAGE_1, 1, SESSION_INIT)
                .suspendsWith(
                    FlowIORequest.Receive(
                        setOf(
                            SessionInfo(INITIATED_SESSION_ID_1, initiatingIdentityMemberName),
                        )
                    )
                )
                .completedSuccessfullyWith("hello")

        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowStatus(FlowStates.RUNNING)
            }

            expectOutputForFlow(FLOW_ID1) {
                flowResumedWithData(mapOf(INITIATED_SESSION_ID_1 to DATA_MESSAGE_1, INITIATED_SESSION_ID_1 to DATA_MESSAGE_2))
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