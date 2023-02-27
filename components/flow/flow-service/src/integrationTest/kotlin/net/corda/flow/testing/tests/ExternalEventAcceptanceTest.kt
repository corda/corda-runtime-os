package net.corda.flow.testing.tests

import net.corda.data.KeyValuePairList
import java.nio.ByteBuffer
import java.util.stream.Stream
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.flow.event.external.ExternalEventResponseErrorType
import net.corda.data.persistence.EntityRequest
import net.corda.data.persistence.EntityResponse
import net.corda.data.persistence.FindEntities
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.flow.external.events.factory.ExternalEventRecord
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.state.FlowCheckpoint
import net.corda.flow.testing.context.FlowServiceTestBase
import net.corda.flow.testing.context.flowResumedWithError
import net.corda.schema.configuration.FlowConfig
import net.corda.utilities.seconds
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.osgi.service.component.annotations.Component
import org.osgi.test.junit5.service.ServiceExtension

@ExtendWith(ServiceExtension::class)
@Execution(ExecutionMode.SAME_THREAD)
class ExternalEventAcceptanceTest : FlowServiceTestBase() {

    private companion object {
        const val REQUEST_ID = "requestId"
        const val TOPIC = "topic"
        const val KEY = "key"
        val FLOW_START_CONTEXT = mapOf("key" to "value")
        val EXTERNAL_EVENT_CONTEXT = FLOW_START_CONTEXT

        val BYTES = "bytes".toByteArray()
        val BYTE_BUFFER = ByteBuffer.wrap(BYTES)

        val ANY_INPUT = EntityRequest(
            ALICE_HOLDING_IDENTITY,
            FindEntities("entity class name", listOf(BYTE_BUFFER)),
            ExternalEventContext(REQUEST_ID, FLOW_ID1, KeyValuePairList(emptyList()))
        )
        val ANY_RESPONSE = EntityResponse(listOf(BYTE_BUFFER))
        const val STRING_INPUT = "this is an input string"
        const val STRING_RESPONSE = "this is an response string"
        val BYTE_ARRAY_INPUT = "this is an input byte array".toByteArray()
        val BYTE_ARRAY_RESPONSE = "this is an response byte array".toByteArray()

        @JvmStatic
        fun factoriesInputAndResponses(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(ConcreteResponseReceivedFactory::class.java, ANY_INPUT, ANY_RESPONSE),
                Arguments.of(ConcreteResponseReceivedFactory::class.java, STRING_INPUT, ANY_RESPONSE),
                Arguments.of(ConcreteResponseReceivedFactory::class.java, BYTE_ARRAY_INPUT, ANY_RESPONSE),
                Arguments.of(AnyResponseReceivedFactory::class.java, ANY_INPUT, ANY_RESPONSE),
                Arguments.of(AnyResponseReceivedFactory::class.java, STRING_INPUT, ANY_RESPONSE),
                Arguments.of(AnyResponseReceivedFactory::class.java, BYTE_ARRAY_INPUT, ANY_RESPONSE),
                Arguments.of(StringResponseReceivedFactory::class.java, ANY_INPUT, STRING_RESPONSE),
                Arguments.of(StringResponseReceivedFactory::class.java, STRING_INPUT, STRING_RESPONSE),
                Arguments.of(StringResponseReceivedFactory::class.java, BYTE_ARRAY_INPUT, STRING_RESPONSE),
                Arguments.of(ByteArrayResponseReceivedFactory::class.java, ANY_INPUT, BYTE_ARRAY_RESPONSE),
                Arguments.of(ByteArrayResponseReceivedFactory::class.java, STRING_INPUT, BYTE_ARRAY_RESPONSE),
                Arguments.of(ByteArrayResponseReceivedFactory::class.java, BYTE_ARRAY_INPUT, BYTE_ARRAY_RESPONSE)
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
            flowConfiguration(FlowConfig.EXTERNAL_EVENT_MESSAGE_RESEND_WINDOW, -50000L)
        }
    }

    @ParameterizedTest(name = "Sending an external event sends a {0} payload created by an ExternalEventFactory")
    @MethodSource("factoriesInputAndResponses")
    fun `Sending an external event sends a payload created by an ExternalEventFactory`(
        factory: Class<out ExternalEventFactory<*, *, *>>,
        input: Any,
        @Suppress("UNUSED_PARAMETER") response: Any
    ) {
        `when` {
            startFlowEventReceived(
                FLOW_ID1,
                REQUEST_ID1,
                ALICE_HOLDING_IDENTITY,
                CPI1,
                "flow start data",
                FLOW_START_CONTEXT
            )
                .suspendsWith(FlowIORequest.ExternalEvent(REQUEST_ID, factory, input, EXTERNAL_EVENT_CONTEXT))
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                externalEvent(TOPIC, KEY, input)
            }
        }
    }

    @ParameterizedTest(name = "Receiving an external event response with a {0} payload with the correct request id resumes the flow")
    @MethodSource("factoriesInputAndResponses")
    fun `Receiving an external event response with the correct request id resumes the flow`(
        factory: Class<out ExternalEventFactory<*, *, *>>,
        input: Any,
        response: Any
    ) {

        given {
            startFlowEventReceived(
                FLOW_ID1,
                REQUEST_ID1,
                ALICE_HOLDING_IDENTITY,
                CPI1,
                "flow start data",
                FLOW_START_CONTEXT
            )
                .suspendsWith(FlowIORequest.ExternalEvent(REQUEST_ID, factory, input, EXTERNAL_EVENT_CONTEXT))
        }

        `when` {
            externalEventReceived(FLOW_ID1, REQUEST_ID, response)
                .suspendsWith(FlowIORequest.ForceCheckpoint)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowResumedWith("return with this: $response")
            }
        }
    }

    @Test
    fun `Receiving an external event response with the wrong request id does not resume the flow and ignores the response`() {
        given {
            startFlowEventReceived(
                FLOW_ID1,
                REQUEST_ID1,
                ALICE_HOLDING_IDENTITY,
                CPI1,
                "flow start data",
                FLOW_START_CONTEXT
            )
                .suspendsWith(
                    FlowIORequest.ExternalEvent(
                        REQUEST_ID,
                        AnyResponseReceivedFactory::class.java,
                        ANY_INPUT,
                        EXTERNAL_EVENT_CONTEXT
                    )
                )
        }

        `when` {
            externalEventReceived(FLOW_ID1, "incorrect request id", ANY_RESPONSE)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
            }
        }
    }

    @Test
    fun `Given a flow has already received its external event response the flow can send another event and receive a response`() {
        given {
            startFlowEventReceived(
                FLOW_ID1,
                REQUEST_ID1,
                ALICE_HOLDING_IDENTITY,
                CPI1,
                "flow start data",
                FLOW_START_CONTEXT
            )
                .suspendsWith(
                    FlowIORequest.ExternalEvent(
                        REQUEST_ID,
                        AnyResponseReceivedFactory::class.java,
                        ANY_INPUT,
                        EXTERNAL_EVENT_CONTEXT
                    )
                )
        }

        `when` {
            externalEventReceived(FLOW_ID1, REQUEST_ID, ANY_RESPONSE)
                .suspendsWith(
                    FlowIORequest.ExternalEvent(
                        REQUEST_ID,
                        StringResponseReceivedFactory::class.java,
                        STRING_INPUT,
                        EXTERNAL_EVENT_CONTEXT
                    )
                )

            externalEventReceived(FLOW_ID1, REQUEST_ID, STRING_RESPONSE)
                .suspendsWith(FlowIORequest.ForceCheckpoint)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                externalEvent(TOPIC, KEY, STRING_INPUT)
            }
            expectOutputForFlow(FLOW_ID1) {
                flowResumedWith("return with this: $STRING_RESPONSE")
            }
        }
    }

    @Test
    fun `Receiving an event does not resend the external event unless a 'transient' error is received`() {

        given {
            startFlowEventReceived(
                FLOW_ID1,
                REQUEST_ID1,
                ALICE_HOLDING_IDENTITY,
                CPI1,
                "flow start data",
                FLOW_START_CONTEXT
            )
                .suspendsWith(
                    FlowIORequest.ExternalEvent(
                        REQUEST_ID,
                        AnyResponseReceivedFactory::class.java,
                        ANY_INPUT,
                        EXTERNAL_EVENT_CONTEXT
                    )
                )
        }

        `when` {
            wakeupEventReceived(FLOW_ID1)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
                noExternalEvent(TOPIC)
            }
        }
    }

    @Test
    fun `Receiving a 'transient' error response resends the external event if the retry window has been surpassed`() {
        given {
            startFlowEventReceived(
                FLOW_ID1,
                REQUEST_ID1,
                ALICE_HOLDING_IDENTITY,
                CPI1,
                "flow start data",
                FLOW_START_CONTEXT
            )
                .suspendsWith(
                    FlowIORequest.ExternalEvent(
                        REQUEST_ID,
                        AnyResponseReceivedFactory::class.java,
                        ANY_INPUT,
                        EXTERNAL_EVENT_CONTEXT
                    )
                )
        }

        `when` {
            externalEventErrorReceived(FLOW_ID1, REQUEST_ID, ExternalEventResponseErrorType.TRANSIENT)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
                externalEvent(TOPIC, KEY, ANY_INPUT)
            }
        }
    }

    @Test
    fun `Receiving a 'transient' error response does not resend the external event if the retry window has not been surpassed`() {
        given {
            flowConfiguration(FlowConfig.EXTERNAL_EVENT_MESSAGE_RESEND_WINDOW, 50000L)

            startFlowEventReceived(
                FLOW_ID1,
                REQUEST_ID1,
                ALICE_HOLDING_IDENTITY,
                CPI1,
                "flow start data",
                FLOW_START_CONTEXT
            )
                .suspendsWith(
                    FlowIORequest.ExternalEvent(
                        REQUEST_ID,
                        AnyResponseReceivedFactory::class.java,
                        ANY_INPUT,
                        EXTERNAL_EVENT_CONTEXT
                    )
                )
        }

        `when` {
            externalEventErrorReceived(FLOW_ID1, REQUEST_ID, ExternalEventResponseErrorType.TRANSIENT)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
                noExternalEvent(TOPIC)
            }
        }
    }

    @Test
    fun `Given a 'transient' error response has been received receiving an event will resend the external event if the retry window has been surpassed`() {
        given {
            flowConfiguration(FlowConfig.EXTERNAL_EVENT_MESSAGE_RESEND_WINDOW, 10.seconds.toMillis())

            startFlowEventReceived(
                FLOW_ID1,
                REQUEST_ID1,
                ALICE_HOLDING_IDENTITY,
                CPI1,
                "flow start data",
                FLOW_START_CONTEXT
            )
                .suspendsWith(
                    FlowIORequest.ExternalEvent(
                        REQUEST_ID,
                        AnyResponseReceivedFactory::class.java,
                        ANY_INPUT,
                        EXTERNAL_EVENT_CONTEXT
                    )
                )
        }

        `when` {
            externalEventErrorReceived(FLOW_ID1, REQUEST_ID, ExternalEventResponseErrorType.TRANSIENT)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                noExternalEvent(TOPIC)
            }
        }

        // Wait for the resend window to be passed
        Thread.sleep(10.seconds.toMillis())

        `when` {
            wakeupEventReceived(FLOW_ID1)
                .suspendsWith(FlowIORequest.ForceCheckpoint)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                externalEvent(TOPIC, KEY, ANY_INPUT)
            }
        }
    }

    @Test
    fun `Given a 'transient' error response has been received receiving a successful response resumes the flow and does not resend the event`() {
        given {
            flowConfiguration(FlowConfig.EXTERNAL_EVENT_MESSAGE_RESEND_WINDOW, -50000L)

            startFlowEventReceived(
                FLOW_ID1,
                REQUEST_ID1,
                ALICE_HOLDING_IDENTITY,
                CPI1,
                "flow start data",
                FLOW_START_CONTEXT
            )
                .suspendsWith(
                    FlowIORequest.ExternalEvent(
                        REQUEST_ID,
                        AnyResponseReceivedFactory::class.java,
                        ANY_INPUT,
                        EXTERNAL_EVENT_CONTEXT
                    )
                )

            externalEventErrorReceived(FLOW_ID1, REQUEST_ID, ExternalEventResponseErrorType.TRANSIENT)
        }

        `when` {
            externalEventReceived(FLOW_ID1, REQUEST_ID, ANY_RESPONSE)
                .suspendsWith(FlowIORequest.ForceCheckpoint)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowResumedWith("return with this: $ANY_RESPONSE")
                noExternalEvent(TOPIC)
            }
        }
    }

    @Test
    fun `Receiving a 'platform' error response resumes the flow with an error`() {
        given {
            startFlowEventReceived(
                FLOW_ID1,
                REQUEST_ID1,
                ALICE_HOLDING_IDENTITY,
                CPI1,
                "flow start data",
                FLOW_START_CONTEXT
            )
                .suspendsWith(
                    FlowIORequest.ExternalEvent(
                        REQUEST_ID,
                        AnyResponseReceivedFactory::class.java,
                        ANY_INPUT,
                        EXTERNAL_EVENT_CONTEXT
                    )
                )
        }

        `when` {
            externalEventErrorReceived(FLOW_ID1, REQUEST_ID, ExternalEventResponseErrorType.PLATFORM)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowResumedWithError<CordaRuntimeException>()
            }
        }
    }

    @Test
    fun `Receiving a 'fatal' error response DLQs the flow and does not resume`() {
        given {
            startFlowEventReceived(
                FLOW_ID1,
                REQUEST_ID1,
                ALICE_HOLDING_IDENTITY,
                CPI1,
                "flow start data",
                FLOW_START_CONTEXT
            )
                .suspendsWith(
                    FlowIORequest.ExternalEvent(
                        REQUEST_ID,
                        AnyResponseReceivedFactory::class.java,
                        ANY_INPUT,
                        EXTERNAL_EVENT_CONTEXT
                    )
                )
        }

        `when` {
            externalEventErrorReceived(FLOW_ID1, REQUEST_ID, ExternalEventResponseErrorType.FATAL)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                markedForDlq()
                flowDidNotResume()
            }
        }
    }

    @Component(service = [ExternalEventFactory::class])
    class ConcreteResponseReceivedFactory : ExternalEventFactory<Any, EntityResponse, Any> {

        override val responseType = EntityResponse::class.java

        override fun createExternalEvent(
            checkpoint: FlowCheckpoint,
            flowExternalEventContext: ExternalEventContext,
            parameters: Any
        ): ExternalEventRecord {
            return ExternalEventRecord(
                TOPIC,
                KEY,
                parameters
            )
        }

        override fun resumeWith(checkpoint: FlowCheckpoint, response: EntityResponse): Any {
            return "return with this: $response"
        }
    }

    @Component(service = [ExternalEventFactory::class])
    class AnyResponseReceivedFactory : ExternalEventFactory<Any, Any, Any> {

        override val responseType = Any::class.java

        override fun createExternalEvent(
            checkpoint: FlowCheckpoint,
            flowExternalEventContext: ExternalEventContext,
            parameters: Any
        ): ExternalEventRecord {
            return ExternalEventRecord(
                TOPIC,
                KEY,
                parameters
            )
        }

        override fun resumeWith(checkpoint: FlowCheckpoint, response: Any): Any {
            return "return with this: $response"
        }
    }

    @Component(service = [ExternalEventFactory::class])
    class StringResponseReceivedFactory : ExternalEventFactory<Any, String, Any> {

        override val responseType = String::class.java

        override fun createExternalEvent(
            checkpoint: FlowCheckpoint,
            flowExternalEventContext: ExternalEventContext,
            parameters: Any
        ): ExternalEventRecord {
            return ExternalEventRecord(
                TOPIC,
                KEY,
                parameters
            )
        }

        override fun resumeWith(checkpoint: FlowCheckpoint, response: String): Any {
            return "return with this: $response"
        }
    }

    @Component(service = [ExternalEventFactory::class])
    class ByteArrayResponseReceivedFactory : ExternalEventFactory<Any, ByteArray, Any> {

        override val responseType = ByteArray::class.java

        override fun createExternalEvent(
            checkpoint: FlowCheckpoint,
            flowExternalEventContext: ExternalEventContext,
            parameters: Any
        ): ExternalEventRecord {
            return ExternalEventRecord(
                TOPIC,
                KEY,
                parameters
            )
        }

        override fun resumeWith(checkpoint: FlowCheckpoint, response: ByteArray): Any {
            return "return with this: $response"
        }
    }
}