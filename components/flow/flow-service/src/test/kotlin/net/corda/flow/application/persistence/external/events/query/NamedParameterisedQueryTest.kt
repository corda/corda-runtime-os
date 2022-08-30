package net.corda.flow.application.persistence.external.events.query

import java.nio.ByteBuffer
import net.corda.flow.application.persistence.external.events.AbstractPersistenceExternalEventFactory
import net.corda.flow.application.persistence.external.events.NamedQueryExternalEventFactory
import net.corda.flow.application.persistence.query.NamedParameterisedQuery
import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.flow.fiber.FlowFiberSerializationService
import net.corda.v5.serialization.SerializedBytes
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class NamedParameterisedQueryTest {

    private class TestObject
    private val externalEventExecutor = mock<ExternalEventExecutor>()
    private val flowFiberSerializationService = mock<FlowFiberSerializationService>()
    private val serializedBytes = mock<SerializedBytes<Any>>()
    private val byteBuffer = ByteBuffer.wrap("bytes".toByteArray())
    private val argumentCaptor = argumentCaptor<Class<out AbstractPersistenceExternalEventFactory<Any>>>()

    @BeforeEach
    fun setup() {
        whenever(flowFiberSerializationService.serialize(any())).thenReturn(serializedBytes)
        whenever(serializedBytes.bytes).thenReturn(byteBuffer.array())
        whenever(
            externalEventExecutor.execute(
                argumentCaptor.capture(),
                any()
            )
        ).thenReturn(listOf(byteBuffer))

        val expectedObj = listOf(TestObject())
        whenever(flowFiberSerializationService.deserializePayload<List<TestObject>>(any(), any())).thenReturn(expectedObj)
    }

    @Test
    fun `test execute named query`() {
        val testQuery = NamedParameterisedQuery(externalEventExecutor, flowFiberSerializationService, "", mutableMapOf(), 0,
            1, TestObject::class.java)
        testQuery.setOffset(1)
        testQuery.setLimit(5)
        testQuery.setParameter("named", "test1")

        testQuery.execute()

        verify(flowFiberSerializationService).deserializePayload<List<TestObject>>(any(), any())
        verify(flowFiberSerializationService).serialize<String>(any())
        assertThat(argumentCaptor.firstValue).isEqualTo(NamedQueryExternalEventFactory::class.java)
    }

    @Test
    fun `test execute named query returns nothing`() {

        whenever(flowFiberSerializationService.deserializePayload<List<TestObject>>(any(), any())).thenReturn(null)

        val testQuery = NamedParameterisedQuery(externalEventExecutor, flowFiberSerializationService, "", mutableMapOf(), 0,
            1, TestObject::class.java)
        testQuery.setOffset(1)
        testQuery.setLimit(5)
        testQuery.setParameter("named", "test1")

        assertThat(testQuery.execute()).isEmpty()

        verify(flowFiberSerializationService).deserializePayload<List<TestObject>>(any(), any())
        verify(flowFiberSerializationService).serialize<String>(any())
        assertThat(argumentCaptor.firstValue).isEqualTo(NamedQueryExternalEventFactory::class.java)
    }

}