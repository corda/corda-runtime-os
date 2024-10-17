package net.corda.uniqueness.checker.impl

import net.corda.data.ExceptionEnvelope
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.identity.HoldingIdentity
import net.corda.data.uniqueness.UniquenessCheckRequestAvro
import net.corda.data.uniqueness.UniquenessCheckResultUnhandledExceptionAvro
import net.corda.data.uniqueness.UniquenessCheckType
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.ledger.libs.uniqueness.UniquenessChecker
import net.corda.ledger.libs.uniqueness.data.UniquenessCheckResponse
import net.corda.messaging.api.records.Record
import net.corda.uniqueness.checker.impl.UniquenessCheckerAvroUtils.toAvro
import net.corda.uniqueness.checker.impl.UniquenessCheckerAvroUtils.toCorda
import net.corda.uniqueness.datamodel.impl.UniquenessCheckResultSuccessImpl
import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorUnhandledException
import net.corda.v5.application.uniqueness.model.UniquenessCheckResultFailure
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.time.Instant

class UniquenessCheckMessageProcessorTests {
    private val externalEventContext = mock<ExternalEventContext>()
    private val flowEvent = mock<FlowEvent>()

    private val avroRequest = UniquenessCheckRequestAvro(
        HoldingIdentity("CN=Alice, O=Alice Corp, L=LDN, C=GB", "ABC"),
        externalEventContext,
        "ABC",
        "ABC",
        emptyList(),
        emptyList(),
        0,
        null,
        Instant.now().plusSeconds(3600),
        UniquenessCheckType.WRITE
    )

    private val uniquenessResponse = UniquenessCheckResponse(
        "ABC",
        UniquenessCheckResultSuccessImpl(Instant.now())
    )

    private val uniquenessResults = mapOf(avroRequest.toCorda() to uniquenessResponse)

    private val uniquenessChecker = mock<UniquenessChecker> {
        on { processRequests(any()) } doReturn (uniquenessResults)
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
        processor.process(avroRequest)
        verify(uniquenessChecker).processRequests(listOf(avroRequest.toCorda()))
    }

    @Test
    fun `when process successfully create success response`() {
        val processor =
            UniquenessCheckMessageProcessor(
                uniquenessChecker, externalEventResponseFactory
            )
        processor.process(avroRequest)
        verify(externalEventResponseFactory).success(externalEventContext, uniquenessResponse.toAvro())
    }

    @Test
    fun `when process successfully return event`() {
        val processor =
            UniquenessCheckMessageProcessor(
                uniquenessChecker, externalEventResponseFactory
            )
        val result = processor.process(avroRequest)
        assertThat(result).isEqualTo(flowEvent)
    }

    @Test
    fun `when process unsuccessfully create error response`() {
        val unknownError = mock<UniquenessCheckErrorUnhandledException> {
            on { unhandledExceptionMessage } doReturn "ERROR"
            on { unhandledExceptionType } doReturn "TYPE"
        }
        val fail = mock<UniquenessCheckResultFailure> {
            on { error } doReturn unknownError
        }

        val failAvro = UniquenessCheckResultUnhandledExceptionAvro(
            ExceptionEnvelope(
                "TYPE",
                "ERROR"
            )
        )

        val errorResponse = mock<UniquenessCheckResponse> {
            on { uniquenessCheckResult } doReturn (fail)
        }
        val uniquenessChecker = mock<UniquenessChecker> {
            on { processRequests(any()) } doReturn (mapOf(avroRequest.toCorda() to errorResponse))
        }
        val processor =
            UniquenessCheckMessageProcessor(
                uniquenessChecker, externalEventResponseFactory
            )
        processor.process(avroRequest)
        verify(externalEventResponseFactory).platformError(
            externalEventContext,
            failAvro.exception
        )
    }
}