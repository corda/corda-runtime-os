package net.corda.flow.testing.tests

import net.corda.data.flow.event.session.SessionAck
import net.corda.data.flow.event.session.SessionClose
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.event.session.SessionError
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.testing.context.FlowServiceTestBase
import net.corda.flow.testing.context.StepSetup
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.osgi.test.junit5.service.ServiceExtension
import java.util.stream.Stream

/**
 * Contains general session related tests that do not fit into one of the more specific [FlowIORequest] tests.
 */
@ExtendWith(ServiceExtension::class)
@Execution(ExecutionMode.SAME_THREAD)
class SessionsAcceptanceTest : FlowServiceTestBase() {

    private companion object {
        @JvmStatic
        fun nonInitSessionEventTypes(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(
                    SessionAck::class.simpleName,
                    { dsl: StepSetup -> dsl.sessionAckEventReceived(FLOW_ID1, SESSION_ID_1, receivedSequenceNum = 1) }
                ),
                Arguments.of(
                    SessionData::class.simpleName,
                    { dsl: StepSetup ->
                        dsl.sessionDataEventReceived(FLOW_ID1, SESSION_ID_1, byteArrayOf(1), sequenceNum = 1, receivedSequenceNum = 1)
                    }),
                Arguments.of(
                    SessionClose::class.simpleName,
                    { dsl: StepSetup -> dsl.sessionCloseEventReceived(FLOW_ID1, SESSION_ID_1, sequenceNum = 1, receivedSequenceNum = 1) }
                ),
                Arguments.of(
                    SessionError::class.simpleName,
                    { dsl: StepSetup -> dsl.sessionErrorEventReceived(FLOW_ID1, SESSION_ID_1, sequenceNum = 1, receivedSequenceNum = 1) }
                ),
            )
        }
    }

    @ParameterizedTest(name = "Receiving a {0} event for a flow that does not exist discards the event")
    @MethodSource("nonInitSessionEventTypes")
    fun `Receiving a non-session init event for a flow that does not exist discards the event`(
        @Suppress("UNUSED_PARAMETER") name: String,
        parameter: (StepSetup) -> Unit
    ) {
        given {
            sessionInitiatingIdentity(ALICE_HOLDING_IDENTITY)
            sessionInitiatedIdentity(BOB_HOLDING_IDENTITY)
        }

        `when` {
            parameter(this)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
                noFlowEvents()
            }
        }
    }
}