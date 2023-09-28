package net.corda.persistence.common

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.flow.external.events.responses.exceptions.CpkNotAvailableException
import net.corda.flow.external.events.responses.exceptions.VirtualNodeException
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.messaging.api.records.Record
import net.corda.persistence.common.exceptions.KafkaMessageSizeException
import net.corda.persistence.common.exceptions.MissingAccountContextPropertyException
import net.corda.persistence.common.exceptions.NullParameterException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.NotSerializableException
import java.sql.SQLException
import javax.persistence.PersistenceException

@Suppress("MaxLineLength")
class ResponseFactoryImplTest {

    private companion object {
        val FLOW_EXTERNAL_EVENT_CONTEXT = ExternalEventContext()
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
    fun `errorResponse creates and returns a transient error response when the input exception is CpkNotAvailableException`() {
        val exception = CpkNotAvailableException("")
        whenever(externalEventResponseFactory.transientError(FLOW_EXTERNAL_EVENT_CONTEXT, exception)).thenReturn(RECORD)
        assertThat(responseFactory.errorResponse(FLOW_EXTERNAL_EVENT_CONTEXT, exception)).isEqualTo(RECORD)
    }

    @Test
    fun `errorResponse creates and returns a transient error response when the input exception is VirtualNodeException`() {
        val exception = VirtualNodeException("")
        whenever(externalEventResponseFactory.transientError(FLOW_EXTERNAL_EVENT_CONTEXT, exception)).thenReturn(RECORD)
        assertThat(responseFactory.errorResponse(FLOW_EXTERNAL_EVENT_CONTEXT, exception)).isEqualTo(RECORD)
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
        whenever(persistenceExceptionCategorizer.categorize(exception)).thenReturn(PersistenceExceptionType.PLATFORM)
        whenever(externalEventResponseFactory.platformError(FLOW_EXTERNAL_EVENT_CONTEXT, exception)).thenReturn(RECORD)
        assertThat(responseFactory.errorResponse(FLOW_EXTERNAL_EVENT_CONTEXT, exception)).isEqualTo(RECORD)
    }

    @Test
    fun `errorResponse creates and returns a transient error response when the input exception is PersistenceException and categorized as transient`() {
        val exception = PersistenceException()
        whenever(persistenceExceptionCategorizer.categorize(exception)).thenReturn(PersistenceExceptionType.TRANSIENT)
        whenever(externalEventResponseFactory.transientError(FLOW_EXTERNAL_EVENT_CONTEXT, exception)).thenReturn(RECORD)
        assertThat(responseFactory.errorResponse(FLOW_EXTERNAL_EVENT_CONTEXT, exception)).isEqualTo(RECORD)
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
        whenever(persistenceExceptionCategorizer.categorize(exception)).thenReturn(PersistenceExceptionType.PLATFORM)
        whenever(externalEventResponseFactory.platformError(FLOW_EXTERNAL_EVENT_CONTEXT, exception)).thenReturn(RECORD)
        assertThat(responseFactory.errorResponse(FLOW_EXTERNAL_EVENT_CONTEXT, exception)).isEqualTo(RECORD)
    }

    @Test
    fun `errorResponse creates and returns a transient error response when the input exception is SQLException and categorized as transient`() {
        val exception = SQLException()
        whenever(persistenceExceptionCategorizer.categorize(exception)).thenReturn(PersistenceExceptionType.TRANSIENT)
        whenever(externalEventResponseFactory.transientError(FLOW_EXTERNAL_EVENT_CONTEXT, exception)).thenReturn(RECORD)
        assertThat(responseFactory.errorResponse(FLOW_EXTERNAL_EVENT_CONTEXT, exception)).isEqualTo(RECORD)
    }

    @Test
    fun `errorResponse creates and returns a platform error response for exceptions without specified behaviour`() {
        val exception = Exception()
        whenever(externalEventResponseFactory.platformError(FLOW_EXTERNAL_EVENT_CONTEXT, exception)).thenReturn(RECORD)
        assertThat(responseFactory.errorResponse(FLOW_EXTERNAL_EVENT_CONTEXT, exception)).isEqualTo(RECORD)
    }

    @Test
    fun `transientErrorResponse creates and returns a transient response`() {
        val exception = Exception()
        whenever(externalEventResponseFactory.transientError(FLOW_EXTERNAL_EVENT_CONTEXT, exception)).thenReturn(RECORD)
        assertThat(responseFactory.transientErrorResponse(FLOW_EXTERNAL_EVENT_CONTEXT, exception)).isEqualTo(RECORD)
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
}