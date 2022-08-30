package net.corda.flow.application.persistence.external.events.query

import java.nio.ByteBuffer
import net.corda.flow.application.persistence.external.events.AbstractPersistenceExternalEventFactory
import net.corda.flow.application.persistence.external.events.FindAllExternalEventFactory
import net.corda.flow.application.persistence.query.PagedFindQuery
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

class PagedFindQueryTest {

    class TestObject
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
    fun `test execute find paged query`() {
        val testQuery = PagedFindQuery(externalEventExecutor, flowFiberSerializationService, TestObject::class.java, 0, 1)
        testQuery.setLimit(10)
        testQuery.setOffset(1)
        testQuery.execute()

        verify(flowFiberSerializationService).deserializePayload<TestObject>(any(), any())
        assertThat(argumentCaptor.firstValue).isEqualTo(FindAllExternalEventFactory::class.java)
    }

    @Test
    fun `test execute find paged query returns nothing`() {
        whenever(flowFiberSerializationService.deserializePayload<List<TestObject>>(any(), any())).thenReturn(null)

        val testQuery = PagedFindQuery(externalEventExecutor, flowFiberSerializationService, TestObject::class.java, 0, 1)
        testQuery.setLimit(10)
        testQuery.setOffset(1)

        assertThat(testQuery.execute()).isEmpty()

        verify(flowFiberSerializationService).deserializePayload<TestObject>(any(), any())
        assertThat(argumentCaptor.firstValue).isEqualTo(FindAllExternalEventFactory::class.java)
    }
}