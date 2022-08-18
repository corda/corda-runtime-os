package net.corda.flow.testing.tests

import java.nio.ByteBuffer
import java.util.stream.Stream
import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.flow.event.external.ExternalEventResponseErrorType
import net.corda.data.flow.event.session.SessionAck
import net.corda.data.persistence.EntityRequest
import net.corda.data.persistence.EntityResponse
import net.corda.data.persistence.FindEntity
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.flow.external.events.factory.ExternalEventRecord
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.state.FlowCheckpoint
import net.corda.flow.testing.context.FlowServiceTestBase
import net.corda.flow.testing.context.StepSetup
import net.corda.flow.testing.context.flowResumedWithError
import net.corda.schema.configuration.FlowConfig
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.params.provider.Arguments
import org.osgi.service.component.annotations.Component
import org.osgi.test.junit5.service.ServiceExtension

@ExtendWith(ServiceExtension::class)
@Execution(ExecutionMode.SAME_THREAD)
class ExternalEventAcceptanceTest : FlowServiceTestBase() {

    private companion object {
        const val requestId = "requestId"
        val bytes = "bytes".toByteArray()
        val byteBuffer = ByteBuffer.wrap(bytes)

        val entityRequest = EntityRequest(
            ALICE_HOLDING_IDENTITY,
            FindEntity("entity class name", byteBuffer),
            ExternalEventContext(requestId, FLOW_ID1)
        )

//        @JvmStatic
//        fun factoryToResponse(): Stream<Arguments> {
//            return Stream.of(
//                Arguments.of(Wakeup::class.simpleName, { dsl: StepSetup -> dsl.wakeupEventReceived(FLOW_ID1) }),
//                Arguments.of(
//                    SessionAck::class.simpleName,
//                    { dsl: StepSetup -> dsl.sessionAckEventReceived(FLOW_ID1, SESSION_ID_1, receivedSequenceNum = 2) }
//                )
//            )
//        }
    }

    @Component(service = [ExternalEventFactory::class])
    class PersistenceFactory : ExternalEventFactory<Any, EntityResponse, Any> {

        override val responseType = EntityResponse::class.java

        override fun createExternalEvent(
            checkpoint: FlowCheckpoint,
            flowExternalEventContext: ExternalEventContext,
            parameters: Any
        ): ExternalEventRecord {
            return ExternalEventRecord(
                "topic",
                "key",
                entityRequest
            )
        }

        override fun resumeWith(checkpoint: FlowCheckpoint, response: EntityResponse): Any {
            return "return with this: $response"
        }
    }

    @Component(service = [ExternalEventFactory::class])
    class StringResponseFactory : ExternalEventFactory<Any, String, Any> {

        override val responseType = String::class.java

        override fun createExternalEvent(
            checkpoint: FlowCheckpoint,
            flowExternalEventContext: ExternalEventContext,
            parameters: Any
        ): ExternalEventRecord {
            return ExternalEventRecord(
                "topic",
                "key",
                entityRequest
            )
        }

        override fun resumeWith(checkpoint: FlowCheckpoint, response: String): Any {
            return "return with this: $response"
        }
    }

    @Component(service = [ExternalEventFactory::class])
    class ByteArrayResponseFactory : ExternalEventFactory<Any, ByteArray, Any> {

        override val responseType = ByteArray::class.java

        override fun createExternalEvent(
            checkpoint: FlowCheckpoint,
            flowExternalEventContext: ExternalEventContext,
            parameters: Any
        ): ExternalEventRecord {
            return ExternalEventRecord(
                "topic",
                "key",
                entityRequest
            )
        }

        override fun resumeWith(checkpoint: FlowCheckpoint, response: ByteArray): Any {
            return "return with this: $response"
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

    @Test
    fun `Sending an external event sends a payload created by a ExternalEventFactory`() {
        `when` {
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
                .suspendsWith(
                    FlowIORequest.ExternalEvent(
                        requestId,
                        PersistenceFactory::class.java,
                        "parameters"
                    )
                )
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                externalEvent("topic", "key", entityRequest)
            }
        }
    }

    @Test
    fun `Receiving an external event response with the correct request id resumes the flow`() {

        val response = EntityResponse(byteBuffer)

        given {
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
                .suspendsWith(
                    FlowIORequest.ExternalEvent(
                        requestId,
                        PersistenceFactory::class.java,
                        "parameters"
                    )
                )
        }

        `when` {
            externalEventReceived(FLOW_ID1, requestId, response)
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
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
                .suspendsWith(
                    FlowIORequest.ExternalEvent(
                        requestId,
                        PersistenceFactory::class.java,
                        "parameters"
                    )
                )
        }

        `when` {
            externalEventReceived(FLOW_ID1, "incorrect request id", EntityResponse(byteBuffer))
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
            }
        }
    }

    @Test
    fun `Receiving an string external event response with the correct request id resumes the flow`() {

        val response = "this is my string response"

        given {
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
                .suspendsWith(
                    FlowIORequest.ExternalEvent(
                        requestId,
                        StringResponseFactory::class.java,
                        "parameters"
                    )
                )
        }

        `when` {
            externalEventReceived(FLOW_ID1, requestId, response)
                .suspendsWith(FlowIORequest.ForceCheckpoint)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowResumedWith("return with this: $response")
            }
        }
    }

    @Test
    fun `Receiving an byte array external event response with the correct request id resumes the flow`() {

        val response = "this is my string response".toByteArray()

        given {
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
                .suspendsWith(
                    FlowIORequest.ExternalEvent(
                        requestId,
                        ByteArrayResponseFactory::class.java,
                        "parameters"
                    )
                )
        }

        `when` {
            externalEventReceived(FLOW_ID1, requestId, response)
                .suspendsWith(FlowIORequest.ForceCheckpoint)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowResumedWith("return with this: $response")
            }
        }
    }

    @Test
    fun `Receiving an event does not resend the external event unless a 'retriable' error is received`() {

        given {
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
                .suspendsWith(
                    FlowIORequest.ExternalEvent(
                        requestId,
                        PersistenceFactory::class.java,
                        "parameters"
                    )
                )
        }

        `when` {
            wakeupEventReceived(FLOW_ID1)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
                noExternalEvent("topic")
            }
        }
    }

    @Test
    fun `Receiving a 'retriable' error response resends the external event if the retry window has been surpassed`() {
        given {
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
                .suspendsWith(
                    FlowIORequest.ExternalEvent(
                        requestId,
                        PersistenceFactory::class.java,
                        "parameters"
                    )
                )
        }

        `when` {
            externalEventErrorReceived(FLOW_ID1, requestId, ExternalEventResponseErrorType.RETRY)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
                externalEvent("topic", "key", entityRequest)
            }
        }
    }

    @Test
    fun `Receiving a 'retriable' error response does not resend the external event if the retry window has not been surpassed`() {
        given {
            flowConfiguration(FlowConfig.EXTERNAL_EVENT_MESSAGE_RESEND_WINDOW, 50000L)

            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
                .suspendsWith(
                    FlowIORequest.ExternalEvent(
                        requestId,
                        PersistenceFactory::class.java,
                        "parameters"
                    )
                )
        }

        `when` {
            externalEventErrorReceived(FLOW_ID1, requestId, ExternalEventResponseErrorType.RETRY)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
                noExternalEvent("topic")
            }
        }
    }

    @Test
    fun `Receiving a 'platform' error response resumes the flow with an error`() {
        given {
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
                .suspendsWith(
                    FlowIORequest.ExternalEvent(
                        requestId,
                        PersistenceFactory::class.java,
                        "parameters"
                    )
                )
        }

        `when` {
            externalEventErrorReceived(FLOW_ID1, requestId, ExternalEventResponseErrorType.PLATFORM_ERROR)
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
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
                .suspendsWith(
                    FlowIORequest.ExternalEvent(
                        requestId,
                        PersistenceFactory::class.java,
                        "parameters"
                    )
                )
        }

        `when` {
            externalEventErrorReceived(FLOW_ID1, requestId, ExternalEventResponseErrorType.FATAL_ERROR)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                markedForDlq()
                flowDidNotResume()
            }
        }
    }

//    @Test
//    fun `Receive a 'retriable' error response, retry the request max times, error response received always, flow errors`() {
//        given {
//            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
//                .suspendsWith(
//                    FlowIORequest.Find(
//                        requestId,
//                        className,
//                        bytes
//                    )
//                )
//        }
//
//        `when` {
//            entityResponseErrorReceived(FLOW_ID1, requestId, Error.VIRTUAL_NODE, ExceptionEnvelope("", ""))
//            entityResponseErrorReceived(FLOW_ID1, requestId, Error.VIRTUAL_NODE, ExceptionEnvelope("", ""))
//            entityResponseErrorReceived(FLOW_ID1, requestId, Error.VIRTUAL_NODE, ExceptionEnvelope("", ""))
//        }
//
//        then {
//            expectOutputForFlow(FLOW_ID1) {
//                flowDidNotResume()
//                entityRequestSent(FindEntity(className, byteBuffer))
//            }
//            expectOutputForFlow(FLOW_ID1) {
//                flowDidNotResume()
//                entityRequestSent(FindEntity(className, byteBuffer))
//            }
//            expectOutputForFlow(FLOW_ID1) {
//                flowResumedWithError(CordaPersistenceException::class.java)
//            }
//        }
//    }
//
//    @Test
//    fun `Receive a 'retriable' error response, retry the request, receive wakeup events, successful response received, flow continues`() {
//        given {
//            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
//                .suspendsWith(
//                    FlowIORequest.Find(
//                        requestId,
//                        className,
//                        bytes
//                    )
//                )
//        }
//
//        `when` {
//            entityResponseErrorReceived(FLOW_ID1, requestId, Error.VIRTUAL_NODE, ExceptionEnvelope("", ""))
//            wakeupEventReceived(FLOW_ID1)
//            wakeupEventReceived(FLOW_ID1)
//            wakeupEventReceived(FLOW_ID1)
//            entityResponseSuccessReceived(FLOW_ID1, requestId, byteBuffer)
//                .suspendsWith(FlowIORequest.ForceCheckpoint)
//        }
//
//        then {
//            expectOutputForFlow(FLOW_ID1) {
//                flowDidNotResume()
//                entityRequestSent(FindEntity(className, byteBuffer))
//            }
//            expectOutputForFlow(FLOW_ID1) {
//                flowDidNotResume()
//                entityRequestSent(FindEntity(className, byteBuffer))
//            }
//            expectOutputForFlow(FLOW_ID1) {
//                flowDidNotResume()
//                entityRequestSent(FindEntity(className, byteBuffer))
//            }
//            expectOutputForFlow(FLOW_ID1) {
//                flowDidNotResume()
//                entityRequestSent(FindEntity(className, byteBuffer))
//            }
//            expectOutputForFlow(FLOW_ID1) {
//                flowResumedWith(byteBuffer)
//                noEntityRequestSent()
//            }
//        }
//    }
//
//    @Test
//    fun `Receive a 'not ready' response, retry the request multiple times, success received eventually, flow continues`() {
//        given {
//            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
//                .suspendsWith(
//                    FlowIORequest.Find(
//                        requestId,
//                        className,
//                        bytes
//                    )
//                )
//        }
//
//        `when` {
//            entityResponseErrorReceived(FLOW_ID1, requestId, Error.NOT_READY, ExceptionEnvelope("", ""))
//            entityResponseErrorReceived(FLOW_ID1, requestId, Error.NOT_READY, ExceptionEnvelope("", ""))
//            entityResponseSuccessReceived(FLOW_ID1, requestId, byteBuffer)
//        }
//
//        then {
//            expectOutputForFlow(FLOW_ID1) {
//                flowDidNotResume()
//                entityRequestSent(FindEntity(className, byteBuffer))
//            }
//            expectOutputForFlow(FLOW_ID1) {
//                flowDidNotResume()
//                entityRequestSent(FindEntity(className, byteBuffer))
//            }
//            expectOutputForFlow(FLOW_ID1) {
//                flowResumedWith(byteBuffer)
//            }
//        }
//    }
//
//    @Test
//    fun `Receive a 'fatal' response, does not retry the request, flow errors`() {
//        given {
//            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
//                .suspendsWith(
//                    FlowIORequest.Find(
//                        requestId,
//                        className,
//                        bytes
//                    )
//                )
//        }
//
//        `when` {
//            entityResponseErrorReceived(FLOW_ID1, requestId, Error.FATAL, ExceptionEnvelope("", ""))
//        }
//
//        then {
//            expectOutputForFlow(FLOW_ID1) {
//                flowResumedWithError(CordaPersistenceException::class.java)
//                noEntityRequestSent()
//            }
//        }
//    }
}