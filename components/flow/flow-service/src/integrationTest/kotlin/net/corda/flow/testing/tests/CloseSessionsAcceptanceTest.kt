package net.corda.flow.testing.tests

import java.util.stream.Stream
import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.event.session.SessionAck
import net.corda.data.flow.event.session.SessionClose
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
class CloseSessionsAcceptanceTest : FlowServiceTestBase() {

    private companion object {
        @JvmStatic
        fun wakeupAndSessionAck(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(Wakeup::class.simpleName, { dsl: StepSetup -> dsl.wakeupEventReceived(FLOW_ID1) }),
                Arguments.of(
                    SessionAck::class.simpleName,
                    { dsl: StepSetup -> dsl.sessionAckEventReceived(FLOW_ID1, SESSION_ID_1, receivedSequenceNum = 2) }
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
                            receivedSequenceNum = 3
                        )
                    }
                ),
                Arguments.of(
                    SessionClose::class.simpleName,
                    { dsl: StepSetup ->
                        dsl.sessionCloseEventReceived(
                            FLOW_ID1,
                            SESSION_ID_2,
                            sequenceNum = 1,
                            receivedSequenceNum = 3
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
                    FlowIORequest.Send(
                        mapOf(FlowIORequest.SessionInfo(SESSION_ID_2, BOB_X500_NAME) to "bytes".toByteArray()),
                    )
                )
            )
        }
    }

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
    fun `Calling 'close' on initiated sessions sends session close events`() {
        given {
            initiateTwoFlows(this)
                .suspendsWith(FlowIORequest.ForceCheckpoint)
        }

        `when` {
            sessionAckEventReceived(FLOW_ID1, SESSION_ID_2, receivedSequenceNum = 2)
                .suspendsWith(FlowIORequest.CloseSessions(setOf(SESSION_ID_1, SESSION_ID_2)))
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                sessionCloseEvents(SESSION_ID_1, SESSION_ID_2)
            }
        }
    }

    @Test
    fun `Calling 'close' on an initiated and closing session sends session close events`() {
        given {
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
                .suspendsWith(FlowIORequest.Send(mapOf(
                    FlowIORequest.SessionInfo(SESSION_ID_1, initiatedIdentityMemberName) to DATA_MESSAGE_0)
                ))

            sessionAckEventReceived(FLOW_ID1, SESSION_ID_1, receivedSequenceNum = 2)
                .suspendsWith(FlowIORequest.Send(mapOf(
                    FlowIORequest.SessionInfo(SESSION_ID_2, initiatedIdentityMemberName) to DATA_MESSAGE_0)
                ))

            sessionCloseEventReceived(FLOW_ID1, SESSION_ID_1, sequenceNum = 1, receivedSequenceNum = 2)
        }

        `when` {
            sessionAckEventReceived(FLOW_ID1, SESSION_ID_2, receivedSequenceNum = 2)
                .suspendsWith(FlowIORequest.CloseSessions(setOf(SESSION_ID_1, SESSION_ID_2)))
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                sessionCloseEvents(SESSION_ID_1, SESSION_ID_2)
            }
        }
    }

    @Test
    fun `Calling 'close' on an initiated and closed session sends a session close event to the initiated session`() {
        given {
            initiateTwoFlows(this, 2)
                .suspendsWith(FlowIORequest.CloseSessions(setOf(SESSION_ID_1)))
        }

        `when` {
            sessionCloseEventReceived(FLOW_ID1, SESSION_ID_1, sequenceNum = 1, receivedSequenceNum = 3)
                .suspendsWith(FlowIORequest.CloseSessions(setOf(SESSION_ID_1, SESSION_ID_2)))
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                sessionCloseEvents(SESSION_ID_2)
            }
        }
    }

    @Test
    fun `Calling 'close' on an initiated and errored session sends a session close event to the initiated session`() {
        given {
            initiateTwoFlows(this, 2)
                .suspendsWith(FlowIORequest.ForceCheckpoint)
        }

        `when` {
            sessionErrorEventReceived(FLOW_ID1, SESSION_ID_1, receivedSequenceNum = 2)
                .suspendsWith(FlowIORequest.CloseSessions(setOf(SESSION_ID_1, SESSION_ID_2)))
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                sessionCloseEvents(SESSION_ID_2)
            }
        }
    }

    @Test
    fun `Calling 'close' on a closed and errored session schedules a wakeup event and sends no session close events`() {
        given {
            initiateTwoFlows(this, 2)
                .suspendsWith(FlowIORequest.CloseSessions(setOf(SESSION_ID_1)))

            sessionCloseEventReceived(FLOW_ID1, SESSION_ID_1, sequenceNum = 1, receivedSequenceNum = 3)
                .suspendsWith(FlowIORequest.ForceCheckpoint)
        }

        `when` {
            sessionErrorEventReceived(FLOW_ID1, SESSION_ID_2, receivedSequenceNum = 2)
                .suspendsWith(FlowIORequest.CloseSessions(setOf(SESSION_ID_1, SESSION_ID_2)))
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                wakeUpEvent()
                sessionCloseEvents()
            }
        }
    }

    @Test
    fun `Calling 'close' on errored sessions schedules a wakeup event and sends no session close events`() {
        given {
            initiateTwoFlows(this, 2)
                .suspendsWith(FlowIORequest.ForceCheckpoint)

            sessionErrorEventReceived(FLOW_ID1, SESSION_ID_1, receivedSequenceNum = 2)
                .suspendsWith(FlowIORequest.ForceCheckpoint)
        }

        `when` {
            sessionErrorEventReceived(FLOW_ID1, SESSION_ID_2, receivedSequenceNum = 2)
                .suspendsWith(FlowIORequest.CloseSessions(setOf(SESSION_ID_1, SESSION_ID_2)))
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                wakeUpEvent()
                sessionCloseEvents()
            }
        }
    }

    @Test
    fun `Receiving an out-of-order session close events does not resume the flow and sends a session ack`() {
        given {
            initiateSingleFlow(this, 2)
                .suspendsWith(FlowIORequest.Receive(setOf(FlowIORequest.SessionInfo(SESSION_ID_1, initiatedIdentityMemberName))))

        }

        `when` {
            sessionCloseEventReceived(FLOW_ID1, SESSION_ID_1, sequenceNum = 2, receivedSequenceNum = 2)
                .suspendsWith(FlowIORequest.ForceCheckpoint)
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
            initiateSingleFlow(this, 2)
                .suspendsWith(FlowIORequest.Receive(setOf(FlowIORequest.SessionInfo(SESSION_ID_1, initiatedIdentityMemberName))))
        }

        `when` {
            sessionCloseEventReceived(FLOW_ID1, SESSION_ID_1, sequenceNum = 1, receivedSequenceNum = 2)
                .suspendsWith(FlowIORequest.ForceCheckpoint)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowResumedWithError(CordaRuntimeException::class.java)
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
            initiateTwoFlows(this, 2)
                .suspendsWith(FlowIORequest.CloseSessions(setOf(SESSION_ID_1, SESSION_ID_2)))
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
            initiateTwoFlows(this, 2)
                .suspendsWith(FlowIORequest.CloseSessions(setOf(SESSION_ID_1)))
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
    fun `Receiving a session data event instead of a close resumes the flow with an error`() {
        given {
            initiateSingleFlow(this, 2)
                .suspendsWith(FlowIORequest.CloseSessions(setOf(SESSION_ID_1)))
        }

        `when` {
            sessionDataEventReceived(FLOW_ID1, SESSION_ID_1, DATA_MESSAGE_1, sequenceNum = 1, receivedSequenceNum = 1)
                .suspendsWith(FlowIORequest.ForceCheckpoint)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowResumedWithError<CordaRuntimeException>()
            }
        }
    }

    @Test
    fun `Given two sessions receiving a single session close event does not resume the flow sends a session ack and schedules session cleanup`() {
        given {
            initiateTwoFlows(this, 2)
                .suspendsWith(FlowIORequest.CloseSessions(setOf(SESSION_ID_1, SESSION_ID_2)))
        }

        `when` {
            sessionCloseEventReceived(FLOW_ID1, SESSION_ID_1, sequenceNum = 1, receivedSequenceNum = 3)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
                sessionAckEvents(SESSION_ID_1)
                scheduleFlowMapperCleanupEvents(SESSION_ID_1)
            }
        }
    }

    @Test
    fun `Given two sessions receiving all session close events resumes the flow sends session acks and schedules session cleanup`() {
        given {
            initiateTwoFlows(this, 2)
                .suspendsWith(FlowIORequest.CloseSessions(setOf(SESSION_ID_1, SESSION_ID_2)))
        }

        `when` {
            sessionCloseEventReceived(FLOW_ID1, SESSION_ID_1, sequenceNum = 1, receivedSequenceNum = 3)

            sessionCloseEventReceived(FLOW_ID1, SESSION_ID_2, sequenceNum = 1, receivedSequenceNum = 3)
                .suspendsWith(FlowIORequest.ForceCheckpoint)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
                sessionAckEvents(SESSION_ID_1)
                scheduleFlowMapperCleanupEvents(SESSION_ID_1)
            }

            expectOutputForFlow(FLOW_ID1) {
                flowResumedWith(Unit)
                sessionAckEvents(SESSION_ID_2)
                scheduleFlowMapperCleanupEvents(SESSION_ID_2)
            }
        }
    }

    /**
     * Given two sessions where:
     *
     * - `SESSION_ID_1` has already received a session close event.
     * - `close` is called.
     * - `SESSION_ID_2` receives a session close event.
     * - `SESSION_ID_1` receives a session ack.
     *
     * The flow will resume.
     */
    @Test
    fun `Given two sessions where one enters WAIT_FOR_FINAL_ACK after calling 'close' resumes the flow after receiving events - 1`() {
        given {
            initiateTwoFlows(this, 2)
                .suspendsWith(FlowIORequest.ForceCheckpoint)

            sessionCloseEventReceived(FLOW_ID1, SESSION_ID_1, sequenceNum = 1, receivedSequenceNum = 2)
                .suspendsWith(FlowIORequest.CloseSessions(setOf(SESSION_ID_1, SESSION_ID_2)))
        }

        `when` {
            sessionCloseEventReceived(FLOW_ID1, SESSION_ID_2, sequenceNum = 1, receivedSequenceNum = 3)

            sessionAckEventReceived(FLOW_ID1, SESSION_ID_1, receivedSequenceNum = 3)
                .suspendsWith(FlowIORequest.ForceCheckpoint)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
                sessionAckEvents(SESSION_ID_2)
                scheduleFlowMapperCleanupEvents(SESSION_ID_2)
            }

            expectOutputForFlow(FLOW_ID1) {
                flowResumedWith(Unit)
                sessionAckEvents()
                scheduleFlowMapperCleanupEvents(SESSION_ID_1)
            }
        }
    }

    /**
     * Given two sessions where:
     *
     * - `SESSION_ID_1` has already received a session close event.
     * - `close` is called.
     * - `SESSION_ID_1` receives a session ack.
     * - `SESSION_ID_2` receives a session close event.
     *
     * The flow will resume.
     */
    @Test
    fun `Given two sessions where one enters WAIT_FOR_FINAL_ACK after calling 'close' resumes the flow after receiving events - 2`() {
        given {
            initiateTwoFlows(this, 2)
                .suspendsWith(FlowIORequest.ForceCheckpoint)

            sessionCloseEventReceived(FLOW_ID1, SESSION_ID_1, sequenceNum = 1, receivedSequenceNum = 2)
                .suspendsWith(FlowIORequest.CloseSessions(setOf(SESSION_ID_1, SESSION_ID_2)))
        }

        `when` {
            sessionAckEventReceived(FLOW_ID1, SESSION_ID_1, receivedSequenceNum = 3)

            sessionCloseEventReceived(FLOW_ID1, SESSION_ID_2, sequenceNum = 1, receivedSequenceNum = 3)
                .suspendsWith(FlowIORequest.ForceCheckpoint)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
                sessionAckEvents()
            }

            expectOutputForFlow(FLOW_ID1) {
                flowResumedWith(Unit)
                sessionAckEvents(SESSION_ID_2)
                scheduleFlowMapperCleanupEvents(SESSION_ID_2)
            }
        }
    }

    @Test
    fun `Given two sessions have already received their session close events when the flow calls 'close' for both sessions at once the flow resumes after receiving session acks from each and schedules session cleanup`() {
        given {
            initiateTwoFlows(this, 2)
                .suspendsWith(FlowIORequest.ForceCheckpoint)

            sessionCloseEventReceived(FLOW_ID1, SESSION_ID_1, sequenceNum = 1, receivedSequenceNum = 2)
                .suspendsWith(FlowIORequest.ForceCheckpoint)
        }

        `when` {
            sessionCloseEventReceived(FLOW_ID1, SESSION_ID_2, sequenceNum = 1, receivedSequenceNum = 2)
                .suspendsWith(FlowIORequest.CloseSessions(setOf(SESSION_ID_1, SESSION_ID_2)))

            sessionAckEventReceived(FLOW_ID1, SESSION_ID_1, receivedSequenceNum = 3)

            sessionAckEventReceived(FLOW_ID1, SESSION_ID_2, receivedSequenceNum = 3)
                .suspendsWith(FlowIORequest.ForceCheckpoint)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                sessionCloseEvents(SESSION_ID_1, SESSION_ID_2)
                scheduleFlowMapperCleanupEvents()
            }

            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
                scheduleFlowMapperCleanupEvents(SESSION_ID_1)
            }

            expectOutputForFlow(FLOW_ID1) {
                flowResumedWith(Unit)
                scheduleFlowMapperCleanupEvents(SESSION_ID_2)
            }
        }
    }

    @Test
    fun `Given two sessions have already received their session close events when the flow calls 'close' for each session individually the flow resumes after receiving session acks respectively and schedules session cleanup`() {
        given {
            initiateTwoFlows(this, 2)
                .suspendsWith(FlowIORequest.ForceCheckpoint)

            sessionCloseEventReceived(FLOW_ID1, SESSION_ID_1, sequenceNum = 1, receivedSequenceNum = 2)
                .suspendsWith(FlowIORequest.ForceCheckpoint)
        }

        `when` {
            sessionCloseEventReceived(FLOW_ID1, SESSION_ID_2, sequenceNum = 1, receivedSequenceNum = 2)
                .suspendsWith(FlowIORequest.CloseSessions(setOf(SESSION_ID_1)))

            sessionAckEventReceived(FLOW_ID1, SESSION_ID_1, receivedSequenceNum = 3)
                .suspendsWith(FlowIORequest.CloseSessions(setOf(SESSION_ID_2)))

            sessionAckEventReceived(FLOW_ID1, SESSION_ID_2, receivedSequenceNum = 3)
                .suspendsWith(FlowIORequest.ForceCheckpoint)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                sessionCloseEvents(SESSION_ID_1)
                scheduleFlowMapperCleanupEvents()
            }

            expectOutputForFlow(FLOW_ID1) {
                flowResumedWith(Unit)
                sessionCloseEvents(SESSION_ID_2)
                scheduleFlowMapperCleanupEvents(SESSION_ID_1)
            }

            expectOutputForFlow(FLOW_ID1) {
                flowResumedWith(Unit)
                scheduleFlowMapperCleanupEvents(SESSION_ID_2)
            }
        }
    }

    @Test
    fun `Given two closed sessions when the flow calls 'close' for both sessions a wakeup event is scheduled and no session close events are sent`() {
        given {
            initiateTwoFlows(this, 2)
                .suspendsWith(FlowIORequest.CloseSessions(setOf(SESSION_ID_1, SESSION_ID_2)))

            sessionCloseEventReceived(FLOW_ID1, SESSION_ID_1, sequenceNum = 1, receivedSequenceNum = 3)
        }

        `when` {
            sessionCloseEventReceived(FLOW_ID1, SESSION_ID_2, sequenceNum = 1, receivedSequenceNum = 3)
                .suspendsWith(FlowIORequest.CloseSessions(setOf(SESSION_ID_1, SESSION_ID_2)))

            wakeupEventReceived(FLOW_ID1)
                .suspendsWith(FlowIORequest.ForceCheckpoint)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                wakeUpEvent()
                sessionCloseEvents()
            }

            expectOutputForFlow(FLOW_ID1) {
                flowResumedWith(Unit)
            }
        }
    }

    @ParameterizedTest(name = "Given a flow suspended with {0} receiving a session close event does not resume the flow and sends a session ack")
    @MethodSource("flowIORequestsThatDoNotWaitForWakeup")
    fun `Given a non-close request type receiving a session close event does not resume the flow and sends a session ack`(
        @Suppress("UNUSED_PARAMETER") name: String,
        request: FlowIORequest<*>
    ) {
        given {
            initiateSingleFlow(this, 2)
                .suspendsWith(request)
        }

        `when` {
            sessionCloseEventReceived(FLOW_ID1, SESSION_ID_1, sequenceNum = 1, receivedSequenceNum = 2)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
                sessionAckEvents(SESSION_ID_1)
            }
        }
    }

    @Test
    fun `Given a flow resumes after receiving session data events calling 'close' on the sessions sends session close events and no session ack for the session that resumed the flow`() {
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
                .suspendsWith(FlowIORequest.CloseSessions(setOf(SESSION_ID_1, SESSION_ID_2)))
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
                sessionAckEvents(SESSION_ID_1)
            }

            expectOutputForFlow(FLOW_ID1) {
                flowResumedWith(mapOf(SESSION_ID_1 to DATA_MESSAGE_1, SESSION_ID_2 to DATA_MESSAGE_2))
                sessionCloseEvents(SESSION_ID_1, SESSION_ID_2)
                sessionAckEvents()
            }
        }
    }

    @Test
    fun `Given two sessions receiving a single session error event does not resume the flow and schedules session cleanup`() {
        given {
            initiateTwoFlows(this, 2)
                .suspendsWith(FlowIORequest.CloseSessions(setOf(SESSION_ID_1, SESSION_ID_2)))
        }

        `when` {
            sessionErrorEventReceived(FLOW_ID1, SESSION_ID_1, receivedSequenceNum = 3)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
                scheduleFlowMapperCleanupEvents(SESSION_ID_1)
            }
        }
    }

    @Test
    fun `Given two sessions receiving two session error events resumes the flow with an error and schedules session cleanup`() {
        given {
            initiateTwoFlows(this, 2)
                .suspendsWith(FlowIORequest.CloseSessions(setOf(SESSION_ID_1, SESSION_ID_2)))
        }

        `when` {
            sessionErrorEventReceived(FLOW_ID1, SESSION_ID_1, receivedSequenceNum = 3)
            sessionErrorEventReceived(FLOW_ID1, SESSION_ID_2, receivedSequenceNum = 3)
                .suspendsWith(FlowIORequest.ForceCheckpoint)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
                scheduleFlowMapperCleanupEvents(SESSION_ID_1)
            }

            expectOutputForFlow(FLOW_ID1) {
                flowResumedWithError<CordaRuntimeException>()
                scheduleFlowMapperCleanupEvents(SESSION_ID_2)
            }
        }
    }

    @Test
    fun `Given two sessions receiving a session error event for one session and a session close event for the other resumes the flow with an error and schedules session cleanup`() {
        given {
            initiateTwoFlows(this, 2)
                .suspendsWith(FlowIORequest.CloseSessions(setOf(SESSION_ID_1, SESSION_ID_2)))
        }

        `when` {
            sessionErrorEventReceived(FLOW_ID1, SESSION_ID_1, receivedSequenceNum = 3)
            sessionCloseEventReceived(FLOW_ID1, SESSION_ID_2, sequenceNum = 1, receivedSequenceNum = 3)
                .suspendsWith(FlowIORequest.ForceCheckpoint)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
                scheduleFlowMapperCleanupEvents(SESSION_ID_1)
            }

            expectOutputForFlow(FLOW_ID1) {
                flowResumedWithError<CordaRuntimeException>()
                sessionAckEvents(SESSION_ID_2)
                scheduleFlowMapperCleanupEvents(SESSION_ID_2)
            }
        }
    }

    @Test
    fun `Given two sessions receiving a session data event for one session and a session close event for the other resumes the flow with an error and schedules session cleanup`() {
        given {
            initiateTwoFlows(this, 2)
                .suspendsWith(FlowIORequest.CloseSessions(setOf(SESSION_ID_1, SESSION_ID_2)))
        }

        `when` {
            sessionDataEventReceived(FLOW_ID1, SESSION_ID_1, DATA_MESSAGE_1, sequenceNum = 1, receivedSequenceNum = 3)
            sessionCloseEventReceived(FLOW_ID1, SESSION_ID_2, sequenceNum = 1, receivedSequenceNum = 3)
                .suspendsWith(FlowIORequest.ForceCheckpoint)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
            }

            expectOutputForFlow(FLOW_ID1) {
                flowResumedWithError<CordaRuntimeException>()
                sessionAckEvents(SESSION_ID_2)
                scheduleFlowMapperCleanupEvents(SESSION_ID_2)
            }
        }
    }

    @Test
    fun `Given two sessions receiving session data events for both sessions resumes the flow with an error and schedules session cleanup`() {
        given {
            initiateTwoFlows(this, 2)
                .suspendsWith(FlowIORequest.CloseSessions(setOf(SESSION_ID_1, SESSION_ID_2)))
        }

        `when` {
            sessionDataEventReceived(FLOW_ID1, SESSION_ID_1, DATA_MESSAGE_1, sequenceNum = 1, receivedSequenceNum = 3)
            sessionDataEventReceived(FLOW_ID1, SESSION_ID_2, DATA_MESSAGE_1, sequenceNum = 1, receivedSequenceNum = 3)
                .suspendsWith(FlowIORequest.ForceCheckpoint)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
                scheduleFlowMapperCleanupEvents(SESSION_ID_1)
            }

            expectOutputForFlow(FLOW_ID1) {
                flowResumedWithError<CordaRuntimeException>()
                scheduleFlowMapperCleanupEvents(SESSION_ID_2)
            }
        }
    }
}
