package net.corda.persistence.common

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.orm.PersistenceExceptionCategorizer
import net.corda.orm.PersistenceExceptionType
import net.corda.flow.external.events.responses.exceptions.CpkNotAvailableException
import net.corda.flow.external.events.responses.exceptions.VirtualNodeException
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.messaging.api.exception.CordaHTTPServerTransientException
import net.corda.messaging.api.records.Record
import net.corda.persistence.common.exceptions.KafkaMessageSizeException
import net.corda.persistence.common.exceptions.MissingAccountContextPropertyException
import net.corda.persistence.common.exceptions.NullParameterException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.NotSerializableException
import java.sql.SQLException
import javax.persistence.PersistenceException

@Suppress("MaxLineLength")
class ResponseFactoryImplTest {

    private companion object {
        val FLOW_EXTERNAL_EVENT_CONTEXT = ExternalEventContext().apply { requestId = "req1" }
        val RECORD = Record("topic", "key", FlowEvent())
    }

    private val externalEventResponseFactory = mock<ExternalEventResponseFactory>()
    private val persistenceExceptionCategorizer = mock<PersistenceExceptionCategorizer>()
    private val responseFactory = ResponseFactoryImpl(externalEventResponseFactory, persistenceExceptionCategorizer)

    @Test
    fun `successResponse creates and returns a successful response`() {
        val payload = "payload"
        whenever(externalEventResponseFactory.success(FLOW_EXTERNAL_EVENT_CONTEXT, payload)).thenReturn(RECORD)
        assertThat(responseFactory.successResponse(FLOW_EXTERNAL_EVENT_CONTEXT, payload)).isEqualTo(RECORD)
    }

    @Test
    fun `errorResponse throws CordaTransientServerException when the input exception is CpkNotAvailableException`() {
        invokeAndAssertTransientException(CpkNotAvailableException("Cpk not available"))
    }

    @Test
    fun `errorResponse throws CordaTransientServerException when the input exception is VirtualNodeException`() {
        invokeAndAssertTransientException(VirtualNodeException("Virtual node not available"))
    }

    @Test
    fun `errorResponse creates and returns a platform error response when the input exception is NotSerializableException`() {
        val exception = NotSerializableException()
        whenever(externalEventResponseFactory.platformError(FLOW_EXTERNAL_EVENT_CONTEXT, exception)).thenReturn(RECORD)
        assertThat(responseFactory.errorResponse(FLOW_EXTERNAL_EVENT_CONTEXT, exception)).isEqualTo(RECORD)
    }

    @Test
    fun `errorResponse creates and returns a platform error response when the input exception is NullParameterException`() {
        val exception = NullParameterException("")
        whenever(externalEventResponseFactory.platformError(FLOW_EXTERNAL_EVENT_CONTEXT, exception)).thenReturn(RECORD)
        assertThat(responseFactory.errorResponse(FLOW_EXTERNAL_EVENT_CONTEXT, exception)).isEqualTo(RECORD)
    }

    @Test
    fun `errorResponse creates and returns a fatal error response when the input exception is KafkaMessageSizeException`() {
        val exception = KafkaMessageSizeException("")
        whenever(externalEventResponseFactory.fatalError(FLOW_EXTERNAL_EVENT_CONTEXT, exception)).thenReturn(RECORD)
        assertThat(responseFactory.errorResponse(FLOW_EXTERNAL_EVENT_CONTEXT, exception)).isEqualTo(RECORD)
    }

    @Test
    fun `errorResponse creates and returns a fatal error response when the input exception is MissingAccountContextPropertyException`() {
        val exception = MissingAccountContextPropertyException()
        whenever(externalEventResponseFactory.fatalError(FLOW_EXTERNAL_EVENT_CONTEXT, exception)).thenReturn(RECORD)
        assertThat(responseFactory.errorResponse(FLOW_EXTERNAL_EVENT_CONTEXT, exception)).isEqualTo(RECORD)
    }

    @Test
    fun `errorResponse creates and returns a fatal error response when the input exception is PersistenceException and categorized as fatal`() {
        val exception = PersistenceException()
        whenever(persistenceExceptionCategorizer.categorize(exception)).thenReturn(PersistenceExceptionType.FATAL)
        whenever(externalEventResponseFactory.fatalError(FLOW_EXTERNAL_EVENT_CONTEXT, exception)).thenReturn(RECORD)
        assertThat(responseFactory.errorResponse(FLOW_EXTERNAL_EVENT_CONTEXT, exception)).isEqualTo(RECORD)
    }

    @Test
    fun `errorResponse creates and returns a platform error response when the input exception is PersistenceException and categorized as platform`() {
        val exception = PersistenceException()
        whenever(persistenceExceptionCategorizer.categorize(exception)).thenReturn(PersistenceExceptionType.DATA_RELATED)
        whenever(externalEventResponseFactory.platformError(FLOW_EXTERNAL_EVENT_CONTEXT, exception)).thenReturn(RECORD)
        assertThat(responseFactory.errorResponse(FLOW_EXTERNAL_EVENT_CONTEXT, exception)).isEqualTo(RECORD)
    }

    @Test
    fun `errorResponse creates and returns a transient error response when the input exception is PersistenceException and categorized as transient`() {
        val exception = PersistenceException("Transient persistence exception")
        whenever(persistenceExceptionCategorizer.categorize(exception)).thenReturn(PersistenceExceptionType.TRANSIENT)
        invokeAndAssertTransientException(exception)
    }

    @Test
    fun `errorResponse creates and returns a fatal error response when the input exception is SQLException and categorized as fatal`() {
        val exception = SQLException()
        whenever(persistenceExceptionCategorizer.categorize(exception)).thenReturn(PersistenceExceptionType.FATAL)
        whenever(externalEventResponseFactory.fatalError(FLOW_EXTERNAL_EVENT_CONTEXT, exception)).thenReturn(RECORD)
        assertThat(responseFactory.errorResponse(FLOW_EXTERNAL_EVENT_CONTEXT, exception)).isEqualTo(RECORD)
    }

    @Test
    fun `errorResponse creates and returns a platform error response when the input exception is SQLException and categorized as platform`() {
        val exception = SQLException()
        whenever(persistenceExceptionCategorizer.categorize(exception)).thenReturn(PersistenceExceptionType.DATA_RELATED)
        whenever(externalEventResponseFactory.platformError(FLOW_EXTERNAL_EVENT_CONTEXT, exception)).thenReturn(RECORD)
        assertThat(responseFactory.errorResponse(FLOW_EXTERNAL_EVENT_CONTEXT, exception)).isEqualTo(RECORD)
    }

    @Test
    fun `errorResponse creates and returns a transient error response when the input exception is SQLException and categorized as transient`() {
        val exception = SQLException("Transient SQL exception")
        whenever(persistenceExceptionCategorizer.categorize(exception)).thenReturn(PersistenceExceptionType.TRANSIENT)
        invokeAndAssertTransientException(exception)
    }

    @Test
    fun `errorResponse creates and returns a platform error response for exceptions without specified behaviour`() {
        val exception = Exception()
        whenever(externalEventResponseFactory.platformError(FLOW_EXTERNAL_EVENT_CONTEXT, exception)).thenReturn(RECORD)
        assertThat(responseFactory.errorResponse(FLOW_EXTERNAL_EVENT_CONTEXT, exception)).isEqualTo(RECORD)
    }

    @Test
    fun `platformErrorResponse creates and returns a platform response`() {
        val exception = Exception()
        whenever(externalEventResponseFactory.platformError(FLOW_EXTERNAL_EVENT_CONTEXT, exception)).thenReturn(RECORD)
        assertThat(responseFactory.platformErrorResponse(FLOW_EXTERNAL_EVENT_CONTEXT, exception)).isEqualTo(RECORD)
    }

    @Test
    fun `fatalErrorResponse creates and returns a fatal response`() {
        val exception = Exception()
        whenever(externalEventResponseFactory.fatalError(FLOW_EXTERNAL_EVENT_CONTEXT, exception)).thenReturn(RECORD)
        assertThat(responseFactory.fatalErrorResponse(FLOW_EXTERNAL_EVENT_CONTEXT, exception)).isEqualTo(RECORD)
    }

    private fun invokeAndAssertTransientException(exception: Exception) {
        val expectedErrorMessage = """
                Transient server exception while processing request '${FLOW_EXTERNAL_EVENT_CONTEXT.requestId}'. Cause: ${exception.message}
            """.trimIndent()

        val e = assertThrows<CordaHTTPServerTransientException> {
            responseFactory.errorResponse(FLOW_EXTERNAL_EVENT_CONTEXT, exception)
        }

        assertThat(e.requestId).isEqualTo(FLOW_EXTERNAL_EVENT_CONTEXT.requestId)
        assertThat(e.message).isEqualTo(expectedErrorMessage)
    }
}