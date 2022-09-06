package net.corda.flow.application.persistence.query

import java.nio.ByteBuffer
import net.corda.flow.application.persistence.external.events.AbstractPersistenceExternalEventFactory
import net.corda.flow.application.persistence.external.events.FindAllExternalEventFactory
import net.corda.flow.application.persistence.external.events.FindAllParameters
import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.flow.fiber.FlowFiberSerializationService
import net.corda.v5.application.persistence.CordaPersistenceException
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.serialization.SerializedBytes
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PagedFindQueryTest {

    class TestObject

    private val externalEventExecutor = mock<ExternalEventExecutor>()
    private val flowFiberSerializationService = mock<FlowFiberSerializationService>()
    private val serializedBytes = mock<SerializedBytes<Any>>()
    private val byteBuffer = ByteBuffer.wrap("bytes".toByteArray())
    private val factoryArgumentCaptor = argumentCaptor<Class<out AbstractPersistenceExternalEventFactory<Any>>>()
    private val parametersArgumentCaptor = argumentCaptor<FindAllParameters>()
    private val serializeArgumentCaptor = argumentCaptor<Any>()

    private val query = PagedFindQuery(
        externalEventExecutor = externalEventExecutor,
        flowFiberSerializationService = flowFiberSerializationService,
        entityClass = TestObject::class.java,
        limit = 1,
        offset = 0
    )

    @BeforeEach
    fun setup() {
        whenever(flowFiberSerializationService.serialize(serializeArgumentCaptor.capture())).thenReturn(serializedBytes)
        whenever(serializedBytes.bytes).thenReturn(byteBuffer.array())
    }

    @Test
    fun `setLimit updates the limit`() {
        whenever(externalEventExecutor.execute(factoryArgumentCaptor.capture(), parametersArgumentCaptor.capture()))
            .thenReturn(listOf(byteBuffer))
        whenever(flowFiberSerializationService.deserialize<TestObject>(any<ByteArray>(), any()))
            .thenReturn(TestObject())

        query.execute()
        assertEquals(1, parametersArgumentCaptor.firstValue.limit)

        query.setLimit(10)
        query.execute()
        assertEquals(10, parametersArgumentCaptor.secondValue.limit)
    }

    @Test
    fun `setOffset updates the offset`() {
        whenever(externalEventExecutor.execute(factoryArgumentCaptor.capture(), parametersArgumentCaptor.capture()))
            .thenReturn(listOf(byteBuffer))
        whenever(flowFiberSerializationService.deserialize<TestObject>(any<ByteArray>(), any()))
            .thenReturn(TestObject())

        query.execute()
        assertEquals(0, parametersArgumentCaptor.firstValue.offset)

        query.setOffset(10)
        query.execute()
        assertEquals(10, parametersArgumentCaptor.secondValue.offset)
    }

    @Test
    fun `setLimit cannot be negative`() {
        assertThrows<IllegalArgumentException> { query.setLimit(-1) }
    }

    @Test
    fun `setOffset cannot be negative`() {
        assertThrows<IllegalArgumentException> { query.setOffset(-1) }
    }

    @Test
    fun `executes find query and returns results`() {
        whenever(externalEventExecutor.execute(factoryArgumentCaptor.capture(), parametersArgumentCaptor.capture()))
            .thenReturn(listOf(byteBuffer))

        query.setLimit(10)
        query.setOffset(1)
        query.execute()

        verify(flowFiberSerializationService).deserialize<TestObject>(any<ByteArray>(), any())
        assertEquals(FindAllExternalEventFactory::class.java, factoryArgumentCaptor.firstValue)
    }

    @Test
    fun `executes find query and returns empty list if no results returned from factory`() {
        whenever(externalEventExecutor.execute(factoryArgumentCaptor.capture(), any()))
            .thenReturn(emptyList())

        query.setLimit(10)
        query.setOffset(1)

        assertThat(query.execute()).isEmpty()

        verify(flowFiberSerializationService, never()).deserialize<TestObject>(any<ByteArray>(), any())
        assertEquals(FindAllExternalEventFactory::class.java, factoryArgumentCaptor.firstValue)
    }

    @Test
    fun `rethrows CordaRuntimeExceptions as CordaPersistenceExceptions`() {
        whenever(externalEventExecutor.execute(factoryArgumentCaptor.capture(), any()))
            .thenThrow(CordaRuntimeException("boom"))

        query.setLimit(10)
        query.setOffset(1)

        assertThrows<CordaPersistenceException> { query.execute() }
    }

    @Test
    fun `does not rethrow general exceptions as CordaPersistenceExceptions`() {
        whenever(externalEventExecutor.execute(factoryArgumentCaptor.capture(), any()))
            .thenThrow(IllegalStateException("boom"))

        query.setLimit(10)
        query.setOffset(1)

        assertThrows<IllegalStateException> { query.execute() }
    }
}