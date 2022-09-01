package net.corda.flow.application.persistence.query

import java.nio.ByteBuffer
import net.corda.flow.application.persistence.external.events.AbstractPersistenceExternalEventFactory
import net.corda.flow.application.persistence.external.events.NamedQueryExternalEventFactory
import net.corda.flow.application.persistence.external.events.NamedQueryParameters
import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.flow.fiber.FlowFiberSerializationService
import net.corda.v5.serialization.SerializedBytes
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class NamedParameterizedQueryTest {

    private class TestObject

    private val externalEventExecutor = mock<ExternalEventExecutor>()
    private val flowFiberSerializationService = mock<FlowFiberSerializationService>()
    private val serializedBytes = mock<SerializedBytes<Any>>()
    private val byteBuffer = ByteBuffer.wrap("bytes".toByteArray())
    private val factoryArgumentCaptor = argumentCaptor<Class<out AbstractPersistenceExternalEventFactory<Any>>>()
    private val parametersArgumentCaptor = argumentCaptor<NamedQueryParameters>()
    private val serializeArgumentCaptor = argumentCaptor<Any>()

    private val query = NamedParameterizedQuery(
        externalEventExecutor = externalEventExecutor,
        flowFiberSerializationService = flowFiberSerializationService,
        queryName = "",
        parameters = mutableMapOf(),
        limit = 1,
        offset = 0,
        expectedClass = TestObject::class.java
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
    fun `setParameter sets a parameter`() {
        whenever(externalEventExecutor.execute(factoryArgumentCaptor.capture(), parametersArgumentCaptor.capture()))
            .thenReturn(listOf(byteBuffer))
        whenever(flowFiberSerializationService.deserialize<TestObject>(any<ByteArray>(), any()))
            .thenReturn(TestObject())

        query.execute()
        assertTrue(parametersArgumentCaptor.firstValue.parameters.isEmpty())

        val parameterNameOne = "one"
        val parameterNameTwo = "two"
        val parameterOne = "param one"
        val parameterTwo = "param two"
        query.setParameter(parameterNameOne, parameterOne)
        query.setParameter(parameterNameTwo, parameterTwo)

        query.execute()

        assertEquals(parameterOne, serializeArgumentCaptor.firstValue)
        assertEquals(parameterTwo, serializeArgumentCaptor.secondValue)

        assertEquals(
            mapOf(parameterNameOne to byteBuffer, parameterNameTwo to byteBuffer),
            parametersArgumentCaptor.secondValue.parameters
        )
    }

    @Test
    fun `setParameters overwrites all parameters`() {
        whenever(externalEventExecutor.execute(factoryArgumentCaptor.capture(), parametersArgumentCaptor.capture()))
            .thenReturn(listOf(byteBuffer))
        whenever(flowFiberSerializationService.deserialize<TestObject>(any<ByteArray>(), any()))
            .thenReturn(TestObject())

        query.execute()
        assertTrue(parametersArgumentCaptor.firstValue.parameters.isEmpty())

        val parameterNameOne = "one"
        val parameterNameTwo = "two"
        val parameterNameThree = "three"
        val parameterOne = "param one"
        val parameterTwo = "param two"
        val parameterThree = "param three"
        query.setParameter(parameterNameOne, parameterOne)
        query.setParameters(mapOf(parameterNameTwo to parameterTwo, parameterNameThree to parameterThree))

        query.execute()

        assertEquals(parameterTwo, serializeArgumentCaptor.firstValue)
        assertEquals(parameterThree, serializeArgumentCaptor.secondValue)

        assertEquals(
            mapOf(parameterNameTwo to byteBuffer, parameterNameThree to byteBuffer),
            parametersArgumentCaptor.secondValue.parameters
        )
    }

    @Test
    fun `executes named query and returns results`() {
        val result = TestObject()
        whenever(externalEventExecutor.execute(factoryArgumentCaptor.capture(), any()))
            .thenReturn(listOf(byteBuffer))
        whenever(flowFiberSerializationService.deserialize<TestObject>(any<ByteArray>(), any()))
            .thenReturn(result)

        query.setOffset(1)
        query.setLimit(5)
        query.setParameter("named", "test1")

        assertEquals(listOf(result), query.execute())

        verify(flowFiberSerializationService).deserialize<TestObject>(any<ByteArray>(), any())
        verify(flowFiberSerializationService).serialize<String>(any())
        assertEquals(NamedQueryExternalEventFactory::class.java, factoryArgumentCaptor.firstValue)
    }

    @Test
    fun `executes named query and returns empty list if no results returned from factory`() {
        whenever(externalEventExecutor.execute(factoryArgumentCaptor.capture(), any()))
            .thenReturn(emptyList())

        query.setOffset(1)
        query.setLimit(5)
        query.setParameter("named", "test1")

        assertThat(query.execute()).isEmpty()

        verify(flowFiberSerializationService, never()).deserialize<TestObject>(any<ByteArray>(), any())
        verify(flowFiberSerializationService).serialize<String>(any())
        assertEquals(NamedQueryExternalEventFactory::class.java, factoryArgumentCaptor.firstValue)
    }

}