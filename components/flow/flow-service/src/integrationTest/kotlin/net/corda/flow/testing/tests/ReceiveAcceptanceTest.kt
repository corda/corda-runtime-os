package net.corda.flow.testing.tests

import java.util.stream.Stream
import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.event.session.SessionAck
import net.corda.data.flow.event.session.SessionData
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.testing.context.FlowServiceTestBase
import net.corda.flow.testing.context.StepSetup
import net.corda.flow.testing.context.flowResumedWithError
import net.corda.flow.testing.context.initiateSingleFlow
import net.corda.flow.testing.context.initiateTwoFlows
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.osgi.test.junit5.service.ServiceExtension

@ExtendWith(ServiceExtension::class)
@Execution(ExecutionMode.SAME_THREAD)
class ReceiveAcceptanceTest : FlowServiceTestBase() {

    private companion object {

        val DATA_MESSAGE_3 = byteArrayOf(3)
        val DATA_MESSAGE_4 = byteArrayOf(4)
        val DATA_MESSAGE_5 = byteArrayOf(5)
        val DATA_MESSAGE_6 = byteArrayOf(6)

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
                    { dsl: StepSetup ->
                        dsl.sessionCloseEventReceived(
                            FLOW_ID1,
                            SESSION_ID_2,
                            sequenceNum = 1,
                            receivedSequenceNum = 2
                        )
                    }
                )
            )
        }

        @JvmStatic
        fun flowIORequestsThatDoNotWaitForWakeup(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(
                    FlowIORequest.Send::class.simpleName,
                    FlowIORequest.Send(mapOf(
                        FlowIORequest.SessionInfo(SESSION_ID_2, BOB_X500_NAME) to DATA_MESSAGE_0),
                    )
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

            sessionInitiatingIdentity(ALICE_HOLDING_IDENTITY)
            sessionInitiatedIdentity(BOB_HOLDING_IDENTITY)

            initiatingToInitiatedFlow(PROTOCOL, FAKE_FLOW_NAME, FAKE_FLOW_NAME)
        }
    }

    @Test
    fun `Receiving an out-of-order session data events does not resume the flow and sends a session ack`() {
        given {
            initiateTwoFlows(this)
                .suspendsWith(
                    FlowIORequest.Receive(
                        setOf(
                            FlowIORequest.SessionInfo(SESSION_ID_1, initiatedIdentityMemberName),
                            FlowIORequest.SessionInfo(SESSION_ID_2, initiatedIdentityMemberName),
                        )
                    )
                )

        }

        `when` {
            sessionDataEventReceived(FLOW_ID1, SESSION_ID_1, DATA_MESSAGE_1, sequenceNum = -1, receivedSequenceNum = 2)
            sessionDataEventReceived(FLOW_ID1, SESSION_ID_1, DATA_MESSAGE_1, sequenceNum = 5, receivedSequenceNum = 2)
            sessionDataEventReceived(FLOW_ID1, SESSION_ID_1, DATA_MESSAGE_1, sequenceNum = 3, receivedSequenceNum = 2)
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
            initiateTwoFlows(this)
                .suspendsWith(
                    FlowIORequest.Receive(
                        setOf(
                            FlowIORequest.SessionInfo(SESSION_ID_1, initiatedIdentityMemberName),
                            FlowIORequest.SessionInfo(SESSION_ID_2, initiatedIdentityMemberName),
                        )
                    )
                )
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
            initiateTwoFlows(this)
                .suspendsWith(FlowIORequest.ForceCheckpoint)

            sessionAckEventReceived(FLOW_ID1, SESSION_ID_2, receivedSequenceNum = 2)
                .suspendsWith(
                    FlowIORequest.Receive(
                        setOf(
                            FlowIORequest.SessionInfo(SESSION_ID_1, initiatedIdentityMemberName),
                        )
                    )
                )
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
    fun `Receiving a session close event instead of a data resumes the flow with an error`() {
        given {
            initiateSingleFlow(this, 2)
                .suspendsWith(
                    FlowIORequest.Receive(
                        setOf(
                            FlowIORequest.SessionInfo(SESSION_ID_1, initiatedIdentityMemberName),
                        )
                    )
                )
        }

        `when` {
            sessionCloseEventReceived(FLOW_ID1, SESSION_ID_1, sequenceNum = 1, receivedSequenceNum = 2)
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
            initiateTwoFlows(this, 2)
                .suspendsWith(FlowIORequest.Receive(setOf(
                    FlowIORequest.SessionInfo(SESSION_ID_1, initiatedIdentityMemberName),
                    FlowIORequest.SessionInfo(SESSION_ID_2, initiatedIdentityMemberName),
                )))
        }

        `when` {
            sessionDataEventReceived(FLOW_ID1, SESSION_ID_1, DATA_MESSAGE_1, sequenceNum = 1, receivedSequenceNum = 2)
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
            initiateTwoFlows(this, 2)
                .suspendsWith(FlowIORequest.Receive(setOf(
                    FlowIORequest.SessionInfo(SESSION_ID_1, initiatedIdentityMemberName),
                    FlowIORequest.SessionInfo(SESSION_ID_2, initiatedIdentityMemberName),
                )))
        }

        `when` {
            sessionDataEventReceived(FLOW_ID1, SESSION_ID_1, DATA_MESSAGE_1, sequenceNum = 1, receivedSequenceNum = 2)

            sessionDataEventReceived(FLOW_ID1, SESSION_ID_2, DATA_MESSAGE_2, sequenceNum = 1, receivedSequenceNum = 2)
                .suspendsWith(FlowIORequest.ForceCheckpoint)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
                sessionAckEvents(SESSION_ID_1)
            }

            expectOutputForFlow(FLOW_ID1) {
                flowResumedWithData(mapOf(SESSION_ID_1 to DATA_MESSAGE_1, SESSION_ID_2 to DATA_MESSAGE_2))
                sessionAckEvents(SESSION_ID_2)
            }
        }
    }

    @Test
    fun `Given two sessions where one has already received a session data event calling 'receive' and then receiving a session data event for the other session resumes the flow and sends a session ack`() {
        given {
            initiateTwoFlows(this, 2)
                .suspendsWith(FlowIORequest.ForceCheckpoint)

            sessionDataEventReceived(FLOW_ID1, SESSION_ID_1, DATA_MESSAGE_1, sequenceNum = 1, receivedSequenceNum = 2)
                .suspendsWith(FlowIORequest.Receive(setOf(
                    FlowIORequest.SessionInfo(SESSION_ID_1, initiatedIdentityMemberName),
                    FlowIORequest.SessionInfo(SESSION_ID_2, initiatedIdentityMemberName),
                )))
        }

        `when` {
            sessionDataEventReceived(FLOW_ID1, SESSION_ID_2, DATA_MESSAGE_2, sequenceNum = 1, receivedSequenceNum = 2)
                .suspendsWith(FlowIORequest.ForceCheckpoint)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowResumedWithData(mapOf(SESSION_ID_1 to DATA_MESSAGE_1, SESSION_ID_2 to DATA_MESSAGE_2))
                sessionAckEvents(SESSION_ID_2)
            }
        }
    }

    @Test
    fun `Given two sessions have already received their session data events when the flow calls 'receive' for both sessions at once the flow should schedule a wakeup event`() {
        given {
            initiateTwoFlows(this, 2)
                .suspendsWith(FlowIORequest.ForceCheckpoint)

            sessionDataEventReceived(FLOW_ID1, SESSION_ID_1, DATA_MESSAGE_1, sequenceNum = 1, receivedSequenceNum = 2)
                .suspendsWith(FlowIORequest.ForceCheckpoint)
        }

        `when` {
            sessionDataEventReceived(FLOW_ID1, SESSION_ID_2, DATA_MESSAGE_2, sequenceNum = 1, receivedSequenceNum = 2)
                .suspendsWith(FlowIORequest.Receive(setOf(
                    FlowIORequest.SessionInfo(SESSION_ID_1, initiatedIdentityMemberName),
                    FlowIORequest.SessionInfo(SESSION_ID_2, initiatedIdentityMemberName),
                )))
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
            initiateTwoFlows(this, 2)
                .suspendsWith(FlowIORequest.ForceCheckpoint)

            sessionDataEventReceived(FLOW_ID1, SESSION_ID_1, DATA_MESSAGE_1, sequenceNum = 1, receivedSequenceNum = 2)
                .suspendsWith(FlowIORequest.ForceCheckpoint)
        }

        `when` {
            sessionDataEventReceived(FLOW_ID1, SESSION_ID_2, DATA_MESSAGE_2, sequenceNum = 1, receivedSequenceNum = 2)
                .suspendsWith(FlowIORequest.Receive(setOf(
                    FlowIORequest.SessionInfo(SESSION_ID_1, initiatedIdentityMemberName),
                )))

            wakeupEventReceived(FLOW_ID1)
                .suspendsWith(FlowIORequest.Receive(setOf(
                    FlowIORequest.SessionInfo(SESSION_ID_2, initiatedIdentityMemberName),
                )))
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                wakeUpEvent()
            }

            expectOutputForFlow(FLOW_ID1) {
                flowResumedWithData(mapOf(SESSION_ID_1 to DATA_MESSAGE_1))
                wakeUpEvent()
            }
        }
    }

    @ParameterizedTest(name = "Given a flow suspended with {0} receiving a session data event does not resume the flow and sends a session ack")
    @MethodSource("flowIORequestsThatDoNotWaitForWakeup")
    fun `Given a non-receive request type receiving a session data event does not resume the flow and sends a session ack`(
        @Suppress("UNUSED_PARAMETER") name: String,
        request: FlowIORequest<*>
    ) {
        given {
            initiateSingleFlow(this, 2)
                .suspendsWith(request)
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
    fun `Given two sessions receiving a single session error event does not resume the flow and schedules session cleanup`() {
        given {
            initiateTwoFlows(this, 2)
                .suspendsWith(FlowIORequest.Receive(setOf(
                    FlowIORequest.SessionInfo(SESSION_ID_1, initiatedIdentityMemberName),
                    FlowIORequest.SessionInfo(SESSION_ID_2, initiatedIdentityMemberName),
                )))
        }

        `when` {
            sessionErrorEventReceived(FLOW_ID1, SESSION_ID_1, receivedSequenceNum = 2)
                .suspendsWith(FlowIORequest.ForceCheckpoint)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
                scheduleFlowMapperCleanupEvents(SESSION_ID_1)
            }
        }
    }

    @Test
    fun `Given two sessions receiving a session data event for one and a session error event for the other resumes the flow with an error and schedules session cleanup`() {
        given {
            initiateTwoFlows(this, 2)
                .suspendsWith(FlowIORequest.Receive(setOf(
                    FlowIORequest.SessionInfo(SESSION_ID_1, initiatedIdentityMemberName),
                    FlowIORequest.SessionInfo(SESSION_ID_2, initiatedIdentityMemberName),
                )))
        }

        `when` {
            sessionDataEventReceived(FLOW_ID1, SESSION_ID_1, DATA_MESSAGE_1, sequenceNum = 1, receivedSequenceNum = 2)
            sessionErrorEventReceived(FLOW_ID1, SESSION_ID_2, receivedSequenceNum = 2)
                .suspendsWith(FlowIORequest.ForceCheckpoint)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
                sessionAckEvents(SESSION_ID_1)
            }

            expectOutputForFlow(FLOW_ID1) {
                flowResumedWithError<CordaRuntimeException>()
                scheduleFlowMapperCleanupEvents(SESSION_ID_2)
            }
        }
    }

    @Test
    fun `Given two sessions receiving a session error event first for one and a session data event for the other resumes the flow with an error and schedules session cleanup`() {
        given {
            initiateTwoFlows(this, 2)
                .suspendsWith(FlowIORequest.Receive(setOf(
                    FlowIORequest.SessionInfo(SESSION_ID_1, initiatedIdentityMemberName),
                    FlowIORequest.SessionInfo(SESSION_ID_2, initiatedIdentityMemberName),
                )))
        }

        `when` {
            sessionErrorEventReceived(FLOW_ID1, SESSION_ID_2, receivedSequenceNum = 2)
            sessionDataEventReceived(FLOW_ID1, SESSION_ID_1, DATA_MESSAGE_1, sequenceNum = 1, receivedSequenceNum = 2)
                .suspendsWith(FlowIORequest.ForceCheckpoint)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
                scheduleFlowMapperCleanupEvents(SESSION_ID_2)
            }

            expectOutputForFlow(FLOW_ID1) {
                flowResumedWithError<CordaRuntimeException>()
                sessionAckEvents(SESSION_ID_1)
            }
        }
    }

    @Test
    fun `Given two sessions receiving a session close event for one session and a session data event for the other resumes the flow with an error`() {
        given {
            initiateTwoFlows(this, 2)
                .suspendsWith(FlowIORequest.Receive(setOf(
                    FlowIORequest.SessionInfo(SESSION_ID_1, initiatedIdentityMemberName),
                    FlowIORequest.SessionInfo(SESSION_ID_2, initiatedIdentityMemberName),
                )))
        }

        `when` {
            sessionDataEventReceived(FLOW_ID1, SESSION_ID_1, DATA_MESSAGE_1, sequenceNum = 1, receivedSequenceNum = 2)
            sessionCloseEventReceived(FLOW_ID1, SESSION_ID_2, sequenceNum = 1, receivedSequenceNum = 2)
                .suspendsWith(FlowIORequest.ForceCheckpoint)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
            }

            expectOutputForFlow(FLOW_ID1) {
                flowResumedWithError<CordaRuntimeException>()
                sessionAckEvents(SESSION_ID_2)
            }
        }
    }

    @Test
    fun `Given two sessions receiving session close events for both sessions resumes the flow with an error`() {
        given {
            initiateTwoFlows(this, 2)
                .suspendsWith(FlowIORequest.Receive(setOf(
                    FlowIORequest.SessionInfo(SESSION_ID_1, initiatedIdentityMemberName),
                    FlowIORequest.SessionInfo(SESSION_ID_2, initiatedIdentityMemberName),
                )))
        }

        `when` {
            sessionCloseEventReceived(FLOW_ID1, SESSION_ID_1, sequenceNum = 1, receivedSequenceNum = 2)
            sessionCloseEventReceived(FLOW_ID1, SESSION_ID_2, sequenceNum = 1, receivedSequenceNum = 2)
                .suspendsWith(FlowIORequest.ForceCheckpoint)
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

    @Test
    fun `Given two sessions receiving a session data and then close event for one session and a session data event for the other resumes the flow`() {
        given {
            initiateTwoFlows(this, 2)
                .suspendsWith(FlowIORequest.Receive(setOf(
                    FlowIORequest.SessionInfo(SESSION_ID_1, initiatedIdentityMemberName),
                    FlowIORequest.SessionInfo(SESSION_ID_2, initiatedIdentityMemberName),
                )))
        }

        `when` {
            sessionDataEventReceived(FLOW_ID1, SESSION_ID_1, DATA_MESSAGE_1, sequenceNum = 1, receivedSequenceNum = 2)
            sessionCloseEventReceived(FLOW_ID1, SESSION_ID_1, sequenceNum = 2, receivedSequenceNum = 2)
            sessionDataEventReceived(FLOW_ID1, SESSION_ID_2, DATA_MESSAGE_2, sequenceNum = 1, receivedSequenceNum = 2)
                .suspendsWith(FlowIORequest.ForceCheckpoint)
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
                flowResumedWithData(mapOf(SESSION_ID_1 to DATA_MESSAGE_1, SESSION_ID_2 to DATA_MESSAGE_2))
                sessionAckEvents(SESSION_ID_2)
            }
        }
    }

    @Test
    fun `Given a session, if it receives an out of order close and then an ordered data event, the flow resumes`() {
        given {
            initiateSingleFlow(this, 2)
                .suspendsWith(FlowIORequest.Receive(setOf(
                    FlowIORequest.SessionInfo(SESSION_ID_1, initiatedIdentityMemberName),
                    FlowIORequest.SessionInfo(SESSION_ID_1, initiatedIdentityMemberName),
                )))
        }

        `when` {
            sessionCloseEventReceived(FLOW_ID1, SESSION_ID_1, sequenceNum = 2, receivedSequenceNum = 2)
            sessionDataEventReceived(FLOW_ID1, SESSION_ID_1, DATA_MESSAGE_1, sequenceNum = 1, receivedSequenceNum = 2)
                .suspendsWith(FlowIORequest.ForceCheckpoint)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
                sessionAckEvents(SESSION_ID_1)
            }

            expectOutputForFlow(FLOW_ID1) {
                flowResumedWithData(mapOf(SESSION_ID_1 to DATA_MESSAGE_1))
                sessionAckEvents(SESSION_ID_1)
            }
        }
    }

    @Test
    fun `Complex messaging flow executing multiple sends and receives with 2 sessions, receives arrive non-sequentially`() {
        given {
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
                .suspendsWith(FlowIORequest.Send(mapOf(
                    FlowIORequest.SessionInfo(SESSION_ID_1, initiatedIdentityMemberName) to DATA_MESSAGE_0,
                    FlowIORequest.SessionInfo(SESSION_ID_2, initiatedIdentityMemberName) to DATA_MESSAGE_0),
                ))

            sessionAckEventReceived(FLOW_ID1, SESSION_ID_1, receivedSequenceNum = 1)

            sessionAckEventReceived(FLOW_ID1, SESSION_ID_2, receivedSequenceNum = 1)
                .suspendsWith(FlowIORequest.Send(
                    mapOf(
                        FlowIORequest.SessionInfo(SESSION_ID_1, initiatedIdentityMemberName) to  DATA_MESSAGE_1,
                        FlowIORequest.SessionInfo(SESSION_ID_2, initiatedIdentityMemberName) to  DATA_MESSAGE_1,
                    )))

            sessionAckEventReceived(FLOW_ID1, SESSION_ID_1, receivedSequenceNum = 2)
                .suspendsWith(FlowIORequest.Send(
                    mapOf(
                        FlowIORequest.SessionInfo(SESSION_ID_1, initiatedIdentityMemberName) to  DATA_MESSAGE_2,
                    )))

            wakeupEventReceived(FLOW_ID1)
                .suspendsWith(FlowIORequest.Send(
                    mapOf(
                        FlowIORequest.SessionInfo(SESSION_ID_2, initiatedIdentityMemberName) to  DATA_MESSAGE_2,
                    )))


            sessionAckEventReceived(FLOW_ID1, SESSION_ID_2, receivedSequenceNum = 2)
                .suspendsWith(FlowIORequest.Receive(
                    setOf(
                        FlowIORequest.SessionInfo(SESSION_ID_1, initiatedIdentityMemberName),
                        FlowIORequest.SessionInfo(SESSION_ID_2, initiatedIdentityMemberName),
                    )))


            sessionDataEventReceived(FLOW_ID1, SESSION_ID_1, DATA_MESSAGE_3, 1, 2, listOf())

            sessionDataEventReceived(FLOW_ID1, SESSION_ID_2, DATA_MESSAGE_3, 1, 2, listOf())
                .suspendsWith(FlowIORequest.Receive(
                    setOf(
                        FlowIORequest.SessionInfo(SESSION_ID_1, initiatedIdentityMemberName),
                        FlowIORequest.SessionInfo(SESSION_ID_2, initiatedIdentityMemberName),
                    )))

            sessionDataEventReceived(FLOW_ID1, SESSION_ID_1, DATA_MESSAGE_4, 2, 3, listOf())
            sessionDataEventReceived(FLOW_ID1, SESSION_ID_1, DATA_MESSAGE_5, 3, 3, listOf())
            sessionDataEventReceived(FLOW_ID1, SESSION_ID_1, DATA_MESSAGE_6, 4, 3, listOf())

            sessionDataEventReceived(FLOW_ID1, SESSION_ID_2, DATA_MESSAGE_4, 2, 3, listOf())
                .suspendsWith(FlowIORequest.Receive(
                    setOf(
                        FlowIORequest.SessionInfo(SESSION_ID_1, initiatedIdentityMemberName)
                    )))
        }

        `when` {
            sessionAckEventReceived(FLOW_ID1, SESSION_ID_2, receivedSequenceNum = 4, outOfOrderSeqNums = listOf(4))
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowResumedWithData(mapOf(SESSION_ID_1 to DATA_MESSAGE_5))
            }
        }
    }
}