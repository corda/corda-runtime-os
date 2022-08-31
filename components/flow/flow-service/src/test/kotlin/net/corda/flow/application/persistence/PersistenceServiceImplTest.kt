package net.corda.flow.application.persistence

import java.nio.ByteBuffer
import net.corda.flow.application.persistence.external.events.AbstractPersistenceExternalEventFactory
import net.corda.flow.application.persistence.external.events.FindExternalEventFactory
import net.corda.flow.application.persistence.external.events.MergeExternalEventFactory
import net.corda.flow.application.persistence.external.events.PersistExternalEventFactory
import net.corda.flow.application.persistence.external.events.RemoveExternalEventFactory
import net.corda.flow.application.persistence.query.PagedQueryFactory
import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.flow.fiber.FlowFiberSerializationService
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.serialization.SerializedBytes
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PersistenceServiceImplTest {

    private val flowFiberSerializationService = mock<FlowFiberSerializationService>()
    private val pagedQueryFactory = mock<PagedQueryFactory>()
    private val serializedBytes = mock<SerializedBytes<Any>>()
    private val externalEventExecutor = mock<ExternalEventExecutor>()

    private lateinit var persistenceService: PersistenceService

    private val byteBuffer = ByteBuffer.wrap("bytes".toByteArray())

    private val argumentCaptor = argumentCaptor<Class<out AbstractPersistenceExternalEventFactory<Any>>>()

    @BeforeEach
    fun setup() {
        persistenceService = PersistenceServiceImpl(externalEventExecutor, pagedQueryFactory, flowFiberSerializationService)

        whenever(flowFiberSerializationService.serialize(any())).thenReturn(serializedBytes)
        whenever(serializedBytes.bytes).thenReturn(byteBuffer.array())
        whenever(
            externalEventExecutor.execute(
                argumentCaptor.capture(),
                any()
            )
        ).thenReturn(listOf(byteBuffer))
    }

    @Test
    fun `persist executes`() {
        persistenceService.persist(TestObject())

        verify(flowFiberSerializationService).serialize(any())
        assertThat(argumentCaptor.firstValue).isEqualTo(PersistExternalEventFactory::class.java)
    }

    @Test
    fun `merge executes successfully`() {
        whenever(flowFiberSerializationService.deserialize<TestObject>(any(), any())).thenReturn(TestObject())

        persistenceService.merge(TestObject())

        verify(flowFiberSerializationService).deserialize<TestObject>(any(), any())
        verify(flowFiberSerializationService).serialize<TestObject>(any())
        assertThat(argumentCaptor.firstValue).isEqualTo(MergeExternalEventFactory::class.java)
    }

    @Test
    fun `remove executes successfully`() {
        whenever(flowFiberSerializationService.deserialize<TestObject>(any(), any())).thenReturn(TestObject())

        persistenceService.remove(TestObject())

        verify(flowFiberSerializationService, times(0)).deserialize<TestObject>(any(), any())

        verify(flowFiberSerializationService).serialize<TestObject>(any())
        assertThat(argumentCaptor.firstValue).isEqualTo(RemoveExternalEventFactory::class.java)
    }

    @Test
    fun `find executes successfully`() {
        val expectedObj = TestObject()
        whenever(flowFiberSerializationService.deserialize<TestObject>(any(), any())).thenReturn(expectedObj)

        assertThat(persistenceService.find(TestObject::class.java, "key")).isEqualTo(expectedObj)

        verify(flowFiberSerializationService).deserialize<TestObject>(any(), any())
        verify(flowFiberSerializationService).serialize<String>(any())
        assertThat(argumentCaptor.firstValue).isEqualTo(FindExternalEventFactory::class.java)
    }

    @Test
    fun `find all executes successfully`() {
        persistenceService.findAll(TestObject::class.java)
        verify(pagedQueryFactory).createPagedFindQuery<TestObject>(any())
    }

    @Test
    fun `named query executes successfully`() {
        persistenceService.query("", TestObject::class.java)
        verify(pagedQueryFactory).createNamedParameterisedQuery<TestObject>(any(), any())
    }

    class TestObject
}