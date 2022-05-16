package net.corda.flow.testing.tests

import net.corda.data.flow.FlowStackItem
import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.event.session.SessionAck
import net.corda.data.flow.event.session.SessionData
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.testing.context.FlowServiceTestBase
import net.corda.flow.testing.context.StepSetup
import org.junit.jupiter.api.BeforeEach
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
        fun wakeupAndSessionAck(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(Wakeup::class.simpleName, { dsl: StepSetup -> dsl.wakeupEventReceived(FLOW_ID1) }),
                Arguments.of(
                    SessionAck::class.simpleName,
                    { dsl: StepSetup -> dsl.sessionAckEventReceived(FLOW_ID1, SESSION_ID_1, receivedSequenceNum = 1) }
                )
            )
        }

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
                            sequenceNum = 1,
                            receivedSequenceNum = 2
                        )
                    }
                ),
                Arguments.of(
                    SessionData::class.simpleName,
                    { dsl: StepSetup -> dsl.sessionCloseEventReceived(FLOW_ID1, SESSION_ID_2, sequenceNum = 1, receivedSequenceNum = 2) }
                )
            )
        }

        @JvmStatic
        fun flowIORequestsExcludingReceiveAndCloseAndSubFlowFinished(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(FlowIORequest.ForceCheckpoint::class.simpleName, FlowIORequest.ForceCheckpoint),
                Arguments.of(FlowIORequest.InitialCheckpoint::class.simpleName, FlowIORequest.InitialCheckpoint),
                Arguments.of(FlowIORequest.InitiateFlow::class.simpleName, FlowIORequest.InitiateFlow(BOB_X500_NAME, SESSION_ID_1)),
                Arguments.of(FlowIORequest.Send::class.simpleName, FlowIORequest.Send(mapOf(SESSION_ID_1 to byteArrayOf(1)))),
            )
        }
    }

    @BeforeEach
    fun beforeEach() {
        given {
            virtualNode(CPI1, ALICE_HOLDING_IDENTITY)
            cpkMetadata(CPI1, CPK1)
            sandboxCpk(CPK1)
            membershipGroupFor(ALICE_HOLDING_IDENTITY)

            sessionInitiatingIdentity(ALICE_HOLDING_IDENTITY)
            sessionInitiatedIdentity(BOB_HOLDING_IDENTITY)
        }
    }

    @Test
    fun `Receiving an out-of-order session data events does not resume the flow and sends a session ack`() {
        given {
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
                .suspendsWith(FlowIORequest.InitiateFlow(initiatedIdentityMemberName, SESSION_ID_1))

            sessionAckEventReceived(FLOW_ID1, SESSION_ID_1, receivedSequenceNum = 1)
                .suspendsWith(FlowIORequest.InitiateFlow(initiatedIdentityMemberName, SESSION_ID_2))

            sessionAckEventReceived(FLOW_ID1, SESSION_ID_2, receivedSequenceNum = 1)
                .suspendsWith(FlowIORequest.Receive(setOf(SESSION_ID_1, SESSION_ID_2)))
        }

        `when` {
            sessionDataEventReceived(FLOW_ID1, SESSION_ID_1, DATA_MESSAGE_1, sequenceNum = -1, receivedSequenceNum = 1)
            sessionDataEventReceived(FLOW_ID1, SESSION_ID_1, DATA_MESSAGE_1, sequenceNum = 5, receivedSequenceNum = 1)
            sessionDataEventReceived(FLOW_ID1, SESSION_ID_1, DATA_MESSAGE_1, sequenceNum = 3, receivedSequenceNum = 1)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
                sessionAckEvents(SESSION_ID_1)
            }

            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
                sessionAckEvents(SESSION_ID_1)
            }

            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
                sessionAckEvents(SESSION_ID_1)
            }
        }
    }

    @ParameterizedTest(name = "Receiving a {0} event does not resume the flow and resends any unacknowledged events")
    @MethodSource("wakeupAndSessionAck")
    fun `Receiving a wakeup or session ack event does not resume the flow and resends any unacknowledged events`(
        @Suppress("UNUSED_PARAMETER") name: String,
        parameter: (StepSetup) -> Unit
    ) {
        given {
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
                .suspendsWith(FlowIORequest.InitiateFlow(initiatedIdentityMemberName, SESSION_ID_1))

            sessionAckEventReceived(FLOW_ID1, SESSION_ID_1, receivedSequenceNum = 1)
                .suspendsWith(FlowIORequest.InitiateFlow(initiatedIdentityMemberName, SESSION_ID_2))

            sessionAckEventReceived(FLOW_ID1, SESSION_ID_2, receivedSequenceNum = 1)
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

    @ParameterizedTest(name = "Receiving a {0} event for an unrelated session does not resume the flow and sends a session ack")
    @MethodSource("unrelatedSessionEvents")
    fun `Receiving a session event for an unrelated session does not resume the flow and sends a session ack`(
        @Suppress("UNUSED_PARAMETER") name: String,
        parameter: (StepSetup) -> Unit
    ) {
        given {
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
                .suspendsWith(FlowIORequest.InitiateFlow(initiatedIdentityMemberName, SESSION_ID_1))

            sessionAckEventReceived(FLOW_ID1, SESSION_ID_1, receivedSequenceNum = 1)
                .suspendsWith(FlowIORequest.InitiateFlow(initiatedIdentityMemberName, SESSION_ID_2))

            sessionAckEventReceived(FLOW_ID1, SESSION_ID_2, receivedSequenceNum = 1)
                .suspendsWith(FlowIORequest.Receive(setOf(SESSION_ID_1)))
        }

        `when` {
            parameter(this)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
                sessionAckEvents(SESSION_ID_2)
            }
        }
    }

    @Test
    fun `Given two sessions receiving a single session data event does not resume the flow and sends a session ack`() {
        given {
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
                .suspendsWith(FlowIORequest.InitiateFlow(initiatedIdentityMemberName, SESSION_ID_1))

            sessionAckEventReceived(FLOW_ID1, SESSION_ID_1, receivedSequenceNum = 1)
                .suspendsWith(FlowIORequest.InitiateFlow(initiatedIdentityMemberName, SESSION_ID_2))

            sessionAckEventReceived(FLOW_ID1, SESSION_ID_2, receivedSequenceNum = 1)
                .suspendsWith(FlowIORequest.Receive(setOf(SESSION_ID_1, SESSION_ID_2)))
        }

        `when` {
            sessionDataEventReceived(FLOW_ID1, SESSION_ID_1, DATA_MESSAGE_1, sequenceNum = 1, receivedSequenceNum = 1)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
                sessionAckEvents(SESSION_ID_1)
            }
        }
    }

    @Test
    fun `Given two sessions receiving all session data events resumes the flow and sends session acks`() {
        given {
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
                .suspendsWith(FlowIORequest.InitiateFlow(initiatedIdentityMemberName, SESSION_ID_1))

            sessionAckEventReceived(FLOW_ID1, SESSION_ID_1, receivedSequenceNum = 1)
                .suspendsWith(FlowIORequest.InitiateFlow(initiatedIdentityMemberName, SESSION_ID_2))

            sessionAckEventReceived(FLOW_ID1, SESSION_ID_2, receivedSequenceNum = 1)
                .suspendsWith(FlowIORequest.Receive(setOf(SESSION_ID_1, SESSION_ID_2)))
        }

        `when` {
            sessionDataEventReceived(FLOW_ID1, SESSION_ID_1, DATA_MESSAGE_1, sequenceNum = 1, receivedSequenceNum = 1)

            sessionDataEventReceived(FLOW_ID1, SESSION_ID_2, DATA_MESSAGE_2, sequenceNum = 1, receivedSequenceNum = 1)
                .suspendsWith(FlowIORequest.ForceCheckpoint)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
                sessionAckEvents(SESSION_ID_1)
            }

            expectOutputForFlow(FLOW_ID1) {
                flowResumedWith(mapOf(SESSION_ID_1 to DATA_MESSAGE_1, SESSION_ID_2 to DATA_MESSAGE_2))
                sessionAckEvents(SESSION_ID_2)
            }
        }
    }

    @Test
    fun `Given two sessions where one has already received a session close event calling 'receive' and then receiving a session data event for the other session resumes the flow and sends a session ack`() {
        given {
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
                .suspendsWith(FlowIORequest.InitiateFlow(initiatedIdentityMemberName, SESSION_ID_1))

            sessionAckEventReceived(FLOW_ID1, SESSION_ID_1, receivedSequenceNum = 1)
                .suspendsWith(FlowIORequest.InitiateFlow(initiatedIdentityMemberName, SESSION_ID_2))

            sessionAckEventReceived(FLOW_ID1, SESSION_ID_2, receivedSequenceNum = 1)
                .suspendsWith(FlowIORequest.ForceCheckpoint)

            sessionDataEventReceived(FLOW_ID1, SESSION_ID_1, DATA_MESSAGE_1, sequenceNum = 1, receivedSequenceNum = 1)

            wakeupEventReceived(FLOW_ID1)
                .suspendsWith(FlowIORequest.Receive(setOf(SESSION_ID_1, SESSION_ID_2)))
        }

        `when` {
            sessionDataEventReceived(FLOW_ID1, SESSION_ID_2, DATA_MESSAGE_2, sequenceNum = 1, receivedSequenceNum = 1)
                .suspendsWith(FlowIORequest.ForceCheckpoint)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowResumedWith(mapOf(SESSION_ID_1 to DATA_MESSAGE_1, SESSION_ID_2 to DATA_MESSAGE_2))
                sessionAckEvents(SESSION_ID_2)
            }
        }
    }

    @Test
    fun `Given two sessions have already received their session data events when the flow calls 'receive' for both sessions at once the flow should schedule a wakeup event`() {
        given {
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
                .suspendsWith(FlowIORequest.InitiateFlow(initiatedIdentityMemberName, SESSION_ID_1))

            sessionAckEventReceived(FLOW_ID1, SESSION_ID_1, receivedSequenceNum = 1)
                .suspendsWith(FlowIORequest.InitiateFlow(initiatedIdentityMemberName, SESSION_ID_2))

            sessionAckEventReceived(FLOW_ID1, SESSION_ID_2, receivedSequenceNum = 1)
                .suspendsWith(FlowIORequest.ForceCheckpoint)

            sessionDataEventReceived(FLOW_ID1, SESSION_ID_1, DATA_MESSAGE_1, sequenceNum = 1, receivedSequenceNum = 1)

            sessionDataEventReceived(FLOW_ID1, SESSION_ID_2, DATA_MESSAGE_2, sequenceNum = 1, receivedSequenceNum = 1)
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
    fun `Given two sessions have already received their session data events when the flow calls 'receive' for each session individually the flow should schedule a wakeup event`() {
        given {
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
                .suspendsWith(FlowIORequest.InitiateFlow(initiatedIdentityMemberName, SESSION_ID_1))

            sessionAckEventReceived(FLOW_ID1, SESSION_ID_1, receivedSequenceNum = 1)
                .suspendsWith(FlowIORequest.InitiateFlow(initiatedIdentityMemberName, SESSION_ID_2))

            sessionAckEventReceived(FLOW_ID1, SESSION_ID_2, receivedSequenceNum = 1)
                .suspendsWith(FlowIORequest.ForceCheckpoint)

            sessionDataEventReceived(FLOW_ID1, SESSION_ID_1, DATA_MESSAGE_1, sequenceNum = 1, receivedSequenceNum = 1)

            sessionDataEventReceived(FLOW_ID1, SESSION_ID_2, DATA_MESSAGE_2, sequenceNum = 1, receivedSequenceNum = 1)
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

    @ParameterizedTest(name = "Given a flow suspended with {0} receiving a session data event does not resume the flow and sends a session ack")
    @MethodSource("flowIORequestsExcludingReceiveAndCloseAndSubFlowFinished")
    fun `Given a non-receive request type receiving a session data event does not resume the flow and sends a session ack`(
        @Suppress("UNUSED_PARAMETER") name: String,
        request: FlowIORequest<*>
    ) {
        given {
            if (request !is FlowIORequest.InitiateFlow) {
                startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
                    .suspendsWith(FlowIORequest.InitiateFlow(initiatedIdentityMemberName, SESSION_ID_1))

                sessionAckEventReceived(FLOW_ID1, SESSION_ID_1, receivedSequenceNum = 1)
                    .suspendsWith(request)
            } else {
                startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
                    .suspendsWith(request)

                sessionAckEventReceived(FLOW_ID1, SESSION_ID_1, receivedSequenceNum = 1)
                    .suspendsWith(FlowIORequest.ForceCheckpoint)
            }
        }

        `when` {
            sessionDataEventReceived(FLOW_ID1, SESSION_ID_1, DATA_MESSAGE_1, sequenceNum = 1, receivedSequenceNum = 1)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
                sessionAckEvents(SESSION_ID_1)
            }
        }
    }
}