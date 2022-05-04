package net.corda.flow.testing.tests

import net.corda.data.flow.FlowStackItem
import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.event.session.SessionAck
import net.corda.data.flow.event.session.SessionClose
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.testing.context.FlowServiceTestBase
import net.corda.flow.testing.context.WhenSetup
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.osgi.test.junit5.service.ServiceExtension
import java.util.stream.Stream

@ExtendWith(ServiceExtension::class)
@Execution(ExecutionMode.SAME_THREAD)
class ReceiveAcceptanceTest : FlowServiceTestBase() {

    private companion object {

        val FLOW_STACK_ITEM = FlowStackItem("flow name", false, listOf())

        @JvmStatic
        fun flowEventsExcludingSessionDataEvents(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(Wakeup::class.simpleName, { dsl: WhenSetup -> dsl.wakeupEventReceived(FLOW_ID1) }),
                Arguments.of(SessionClose::class.simpleName, { dsl: WhenSetup -> dsl.sessionCloseEventReceived(FLOW_ID1, SESSION_ID_1, 1) }),
                Arguments.of(SessionAck::class.simpleName, { dsl: WhenSetup -> dsl.sessionAckEventReceived(FLOW_ID1, SESSION_ID_1, 1) })
            )
        }

        @JvmStatic
        fun flowIORequestsExcludingReceive(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(FlowIORequest.ForceCheckpoint::class.simpleName, FlowIORequest.ForceCheckpoint),
                Arguments.of(FlowIORequest.InitialCheckpoint::class.simpleName, FlowIORequest.InitialCheckpoint),
                Arguments.of(FlowIORequest.CloseSessions::class.simpleName, FlowIORequest.CloseSessions(setOf(SESSION_ID_1))),
                Arguments.of(FlowIORequest.InitiateFlow::class.simpleName, FlowIORequest.InitiateFlow(ALICE_X500_NAME, SESSION_ID_1)),
                Arguments.of(FlowIORequest.Send::class.simpleName, FlowIORequest.Send(mapOf(SESSION_ID_1 to byteArrayOf(1)))),
                Arguments.of(FlowIORequest.SubFlowFinished::class.simpleName, FlowIORequest.SubFlowFinished(FLOW_STACK_ITEM)),
                Arguments.of(FlowIORequest.SubFlowFailed::class.simpleName, FlowIORequest.SubFlowFailed(RuntimeException(), FLOW_STACK_ITEM)),
            )
        }
    }

    @Test
    fun `(Receive) Receiving an out-of-order message does not resume the flow and sends a session ack`() {
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

            sessionAckEventReceived(FLOW_ID1, SESSION_ID_2, 1)
                .suspendsWith(FlowIORequest.Receive(setOf(SESSION_ID_1, SESSION_ID_2)))
        }

        `when` {
            sessionDataEventReceived(FLOW_ID1, SESSION_ID_1, DATA_MESSAGE_1, -1)
            sessionDataEventReceived(FLOW_ID1, SESSION_ID_1, DATA_MESSAGE_1, 5)
            sessionDataEventReceived(FLOW_ID1, SESSION_ID_1, DATA_MESSAGE_1, 3)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
                sessionAckEvent(SESSION_ID_1)
            }

            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
                sessionAckEvent(SESSION_ID_1)
            }

            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
                sessionAckEvent(SESSION_ID_1)
            }
        }
    }

    @ParameterizedTest(name = "(Receive) Receiving a {0} event does not resume the flow and resends any unacknowledged events")
    @MethodSource("flowEventsExcludingSessionDataEvents")
    fun `(Receive) Receiving a non-session-data event does not resume the flow and resends any unacknowledged events`(@Suppress("UNUSED_PARAMETER") name: String, parameter: (WhenSetup) -> Unit) {
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

            sessionAckEventReceived(FLOW_ID1, SESSION_ID_2, 1)
                .suspendsWith(FlowIORequest.Receive(setOf(SESSION_ID_1, SESSION_ID_2)))
        }

        `when` {
            parameter(this)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
                // Need to rollback time to do the resends?
            }
        }
    }

    @Test
    fun `(Receive) Receiving a session data event for an unrelated session does not resume the flow and sends a session ack`() {
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

            sessionAckEventReceived(FLOW_ID1, SESSION_ID_2, 1)
                .suspendsWith(FlowIORequest.Receive(setOf(SESSION_ID_1)))
        }

        `when` {
            sessionDataEventReceived(FLOW_ID1, SESSION_ID_2, DATA_MESSAGE_1, 1)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
                sessionAckEvent(SESSION_ID_2)
            }
        }
    }

    @Test
    fun `(Receive) Receiving a session close event for an unrelated session does not resume the flow and sends a session ack`() {
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

            sessionAckEventReceived(FLOW_ID1, SESSION_ID_2, 1)
                .suspendsWith(FlowIORequest.Receive(setOf(SESSION_ID_1)))
        }

        `when` {
            sessionCloseEventReceived(FLOW_ID1, SESSION_ID_2, 1)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
                sessionAckEvent(SESSION_ID_2)
            }
        }
    }

    @Test
    fun `(Receive) Given two sessions receiving a single session data event does not resume the flow and sends a session ack`() {
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

            sessionAckEventReceived(FLOW_ID1, SESSION_ID_2, 1)
                .suspendsWith(FlowIORequest.Receive(setOf(SESSION_ID_1, SESSION_ID_2)))
        }

        `when` {
            sessionDataEventReceived(FLOW_ID1, SESSION_ID_1, DATA_MESSAGE_1, 1)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
                sessionAckEvent(SESSION_ID_1)
            }
        }
    }

    @Test
    fun `(Receive) Given two sessions receiving all session data events resumes the flow and sends session acks`() {
        given {
            // Background setup
            virtualNode(CPI1, ALICE_HOLDING_IDENTITY)
            cpkMetadata(CPI1, CPK1)
            sandboxCpk(CPK1)
            membershipGroupFor(ALICE_HOLDING_IDENTITY)

            // Bob will be initiating sessions with Alice
            sessionInitiatingIdentity(ALICE_HOLDING_IDENTITY)
            sessionInitiatedIdentity(BOB_HOLDING_IDENTITY)

            // Bob starts a flow an initiates a session with Alice
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
                .suspendsWith(FlowIORequest.InitiateFlow(initiatedIdentityMemberName, SESSION_ID_1))

            // Alice acknowledges the session and Bob creates a second session
            sessionAckEventReceived(FLOW_ID1, SESSION_ID_1, 1)
                .suspendsWith(FlowIORequest.InitiateFlow(initiatedIdentityMemberName, SESSION_ID_2))

            // Alice acknowledges the second session and Bob requests to receive data for both
            // sessions
            sessionAckEventReceived(FLOW_ID1, SESSION_ID_2, 1)
                .suspendsWith(FlowIORequest.Receive(setOf(SESSION_ID_1, SESSION_ID_2)))
        }

        `when` {
            // Alice sends the data for session 1
            sessionDataEventReceived(FLOW_ID1, SESSION_ID_1, DATA_MESSAGE_1, 1)

            // Alice sends the data for session 2
            sessionDataEventReceived(FLOW_ID1, SESSION_ID_2, DATA_MESSAGE_2, 1)
                .suspendsWith(FlowIORequest.ForceCheckpoint)
        }

        then {
            // As Bob has requested receive for both sessions we expect Bob to acknowledge the
            // receipt of the data for session 1 but not wake up the flow until all data is received.
            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
                sessionAckEvent(SESSION_ID_1)
            }

            expectOutputForFlow(FLOW_ID1) {
                flowResumedWith(mapOf(SESSION_ID_1 to DATA_MESSAGE_1, SESSION_ID_2 to DATA_MESSAGE_2))
                sessionAckEvent(SESSION_ID_2)
                wakeUpEvent()
            }
        }
    }

    @Test
    fun `(Receive) Given two sessions have already received their session data events when the flow calls 'receive' for both sessions at once the flow should schedule a wakeup event`() {
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

            sessionAckEventReceived(FLOW_ID1, SESSION_ID_2, 1)
                .suspendsWith(FlowIORequest.ForceCheckpoint)

            sessionDataEventReceived(FLOW_ID1, SESSION_ID_1, DATA_MESSAGE_1, 1)

            sessionDataEventReceived(FLOW_ID1, SESSION_ID_2, DATA_MESSAGE_2, 1)
        }

        `when` {
            wakeupEventReceived(FLOW_ID1)
                .suspendsWith(FlowIORequest.Receive(setOf(SESSION_ID_1, SESSION_ID_2)))
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                wakeUpEvent()
            }
        }
    }

    @Test
    fun `(Receive) Given two sessions have already received their session data events when the flow calls 'receive' for each session individually the flow should schedule a wakeup event`() {
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

            sessionAckEventReceived(FLOW_ID1, SESSION_ID_2, 1)
                .suspendsWith(FlowIORequest.ForceCheckpoint)

            sessionDataEventReceived(FLOW_ID1, SESSION_ID_1, DATA_MESSAGE_1, 1)

            sessionDataEventReceived(FLOW_ID1, SESSION_ID_2, DATA_MESSAGE_2, 1)
        }

        `when` {
            wakeupEventReceived(FLOW_ID1)
                .suspendsWith(FlowIORequest.Receive(setOf(SESSION_ID_1)))

            wakeupEventReceived(FLOW_ID1)
                .suspendsWith(FlowIORequest.Receive(setOf(SESSION_ID_2)))
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                wakeUpEvent()
            }

            expectOutputForFlow(FLOW_ID1) {
                flowResumedWith(mapOf(SESSION_ID_1 to DATA_MESSAGE_1))
                wakeUpEvent()
            }
        }
    }

    @ParameterizedTest(name = "(Any-non-receive-request-type) Given a flow suspended with {0} receiving a session data event does not resume the flow and sends a session ack")
    @MethodSource("flowIORequestsExcludingReceive")
    fun `(Any-non-receive-request-type) Receiving a session data event does not resume the flow and sends a session ack`(@Suppress("UNUSED_PARAMETER") name: String, request: FlowIORequest<*>) {
        given {
            virtualNode(CPI1, ALICE_HOLDING_IDENTITY)
            cpkMetadata(CPI1, CPK1)
            sandboxCpk(CPK1)
            membershipGroupFor(ALICE_HOLDING_IDENTITY)

            sessionInitiatingIdentity(ALICE_HOLDING_IDENTITY)
            sessionInitiatedIdentity(BOB_HOLDING_IDENTITY)

            if (request !is FlowIORequest.InitiateFlow) {
                startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
                    .suspendsWith(FlowIORequest.InitiateFlow(initiatedIdentityMemberName, SESSION_ID_1))

                sessionAckEventReceived(FLOW_ID1, SESSION_ID_1, 1)
                    .suspendsWith(request)
            } else {
                startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
                    .suspendsWith(request)

                sessionAckEventReceived(FLOW_ID1, SESSION_ID_1, 1)
                    .suspendsWith(FlowIORequest.ForceCheckpoint)
            }
        }

        `when` {
            sessionDataEventReceived(FLOW_ID1, SESSION_ID_1, DATA_MESSAGE_1, 1)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
            }
        }
    }
}