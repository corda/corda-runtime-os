package net.corda.uniqueness.checker.impl

import net.corda.data.ExceptionEnvelope
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.uniqueness.UniquenessCheckRequestAvro
import net.corda.data.uniqueness.UniquenessCheckResponseAvro
import net.corda.data.uniqueness.UniquenessCheckResultUnhandledExceptionAvro
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.messaging.api.records.Record
import net.corda.uniqueness.checker.UniquenessChecker
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class UniquenessCheckMessageProcessorTests {
    private val externalEventContext = mock<ExternalEventContext>()
    private val flowEvent = mock<FlowEvent>()
    private val request = mock<UniquenessCheckRequestAvro> {
        on { flowExternalEventContext } doReturn (externalEventContext)
    }
    private val response = mock<UniquenessCheckResponseAvro>()
    private val results = mapOf(request to response)
    private val uniquenessChecker = mock<UniquenessChecker> {
        on { processRequests(any()) } doReturn (results)
    }
    private val externalEventResponseFactory = mock<ExternalEventResponseFactory> {
        on { success(any(), any()) } doReturn (Record("batman", "mobile", flowEvent))
        on { platformError(any(), any<ExceptionEnvelope>()) } doReturn (Record("joker", "face", flowEvent))
    }

    @Test
    fun `when process call uniqueness checker`() {
        val processor =
            UniquenessCheckMessageProcessor(
                uniquenessChecker, externalEventResponseFactory
            )
        processor.process(request)
        verify(uniquenessChecker).processRequests(listOf(request))
    }

    @Test
    fun `when process successfully create success response`() {
        val processor =
            UniquenessCheckMessageProcessor(
                uniquenessChecker, externalEventResponseFactory
            )
        processor.process(request)
        verify(externalEventResponseFactory).success(externalEventContext, response)
    }

    @Test
    fun `when process successfully return event`() {
        val processor =
            UniquenessCheckMessageProcessor(
                uniquenessChecker, externalEventResponseFactory
            )
        val result = processor.process(request)
        assertThat(result).isEqualTo(flowEvent)
    }

    @Test
    fun `when process unsuccessfully create error response`() {
        val ex = ExceptionEnvelope()
        val errorMsg = mock<UniquenessCheckResultUnhandledExceptionAvro> {
            on { exception } doReturn (ex)
        }
        val errorResponse = mock<UniquenessCheckResponseAvro> {
            on { result } doReturn (errorMsg)
        }
        val uniquenessChecker = mock<UniquenessChecker> {
            on { processRequests(any()) } doReturn (mapOf(request to errorResponse))
        }
        val processor =
            UniquenessCheckMessageProcessor(
                uniquenessChecker, externalEventResponseFactory
            )
        processor.process(request)
        verify(externalEventResponseFactory).platformError(externalEventContext, ex)
    }
}