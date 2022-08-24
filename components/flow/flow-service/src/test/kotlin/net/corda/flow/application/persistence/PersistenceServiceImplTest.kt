package net.corda.flow.application.persistence

import java.nio.ByteBuffer
import net.corda.flow.application.persistence.external.events.AbstractPersistenceExternalEventFactory
import net.corda.flow.application.persistence.external.events.FindAllExternalEventFactory
import net.corda.flow.application.persistence.external.events.FindExternalEventFactory
import net.corda.flow.application.persistence.external.events.MergeExternalEventFactory
import net.corda.flow.application.persistence.external.events.PersistExternalEventFactory
import net.corda.flow.application.persistence.external.events.RemoveExternalEventFactory
import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.flow.fiber.FlowFiber
import net.corda.flow.fiber.FlowFiberExecutionContext
import net.corda.flow.fiber.FlowFiberService
import net.corda.flow.pipeline.sandbox.FlowSandboxGroupContext
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.serialization.SerializedBytes
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PersistenceServiceImplTest {

    private val flowFiberService = mock<FlowFiberService>()
    private val flowFiber = mock<FlowFiber>()
    private val executionContext = mock<FlowFiberExecutionContext>()
    private val sandboxGroupContext = mock<FlowSandboxGroupContext>()
    private val serializationService = mock<SerializationService>()
    private val serializedBytes = mock<SerializedBytes<Any>>()
    private val externalEventExecutor = mock<ExternalEventExecutor>()

    private lateinit var persistenceService: PersistenceService

    private val byteBuffer = ByteBuffer.wrap("bytes".toByteArray())

    private val argumentCaptor = argumentCaptor<Class<out AbstractPersistenceExternalEventFactory<Any>>>()

    @BeforeEach
    fun setup() {
        persistenceService = PersistenceServiceImpl(externalEventExecutor, flowFiberService)

        whenever(flowFiberService.getExecutingFiber()).thenReturn(flowFiber)
        whenever(flowFiber.getExecutionContext()).thenReturn(executionContext)
        whenever(executionContext.sandboxGroupContext).thenReturn(sandboxGroupContext)
        whenever(sandboxGroupContext.amqpSerializer).thenReturn(serializationService)
        whenever(serializationService.serialize(any())).thenReturn(serializedBytes)
        whenever(serializedBytes.bytes).thenReturn(byteBuffer.array())
        whenever(
            externalEventExecutor.execute(
                argumentCaptor.capture(),
                any()
            )
        ).thenReturn(byteBuffer.array())
    }

    @Test
    fun `persist executes`() {
        persistenceService.persist(TestObject())

        verify(serializationService).serialize(any())
        assertThat(argumentCaptor.firstValue).isEqualTo(PersistExternalEventFactory::class.java)
    }

    @Test
    fun `merge executes successfully`() {
        whenever(flowFiber.suspend<ByteBuffer?>(any())).thenReturn(byteBuffer)
        whenever(serializationService.deserialize<TestObject>(any<ByteArray>(), any())).thenReturn(TestObject())

        persistenceService.merge(TestObject())

        verify(serializationService).deserialize<TestObject>(any<ByteArray>(), any())
        verify(serializationService).serialize<TestObject>(any())
        assertThat(argumentCaptor.firstValue).isEqualTo(MergeExternalEventFactory::class.java)
    }

    @Test
    fun `merge fails when deserializes to wrong type`() {
        whenever(flowFiber.suspend<ByteBuffer?>(any())).thenReturn(byteBuffer)
        whenever(serializationService.deserialize<Any>(any<ByteArray>(), any())).thenReturn(FailTestObject())

        assertThrows<CordaRuntimeException> { persistenceService.merge(TestObject()) }

        verify(serializationService).deserialize<TestObject>(any<ByteArray>(), any())
        verify(serializationService).serialize<TestObject>(any())
        assertThat(argumentCaptor.firstValue).isEqualTo(MergeExternalEventFactory::class.java)
    }

    @Test
    fun `remove executes successfully`() {
        whenever(flowFiber.suspend<ByteBuffer?>(any())).thenReturn(byteBuffer)
        whenever(serializationService.deserialize<TestObject>(any<ByteArray>(), any())).thenReturn(TestObject())

        persistenceService.remove(TestObject())

        verify(serializationService, times(0)).deserialize<TestObject>(any<ByteArray>(), any())
        verify(serializationService).serialize<TestObject>(any())
        assertThat(argumentCaptor.firstValue).isEqualTo(RemoveExternalEventFactory::class.java)
    }

    @Test
    fun `find executes successfully`() {
        val expectedObj = TestObject()
        whenever(flowFiber.suspend<ByteBuffer?>(any())).thenReturn(byteBuffer)
        whenever(serializationService.deserialize<TestObject>(any<ByteArray>(), any())).thenReturn(expectedObj)

        assertThat(persistenceService.find(TestObject::class.java, "key")).isEqualTo(expectedObj)

        verify(serializationService).deserialize<TestObject>(any<ByteArray>(), any())
        verify(serializationService).serialize<String>(any())
        assertThat(argumentCaptor.firstValue).isEqualTo(FindExternalEventFactory::class.java)
    }

    @Test
    fun `find fails when deserializes to wrong type`() {
        whenever(flowFiber.suspend<ByteBuffer?>(any())).thenReturn(byteBuffer)
        whenever(serializationService.deserialize<Any>(any<ByteArray>(), any())).thenReturn(FailTestObject())

        assertThrows<CordaRuntimeException> { persistenceService.find(TestObject::class.java, "key") }

        verify(serializationService).deserialize<TestObject>(any<ByteArray>(), any())
        verify(serializationService).serialize<String>(any())
        assertThat(argumentCaptor.firstValue).isEqualTo(FindExternalEventFactory::class.java)
    }

    @Test
    fun `find all executes successfully`() {
        val singleItem = TestObject()
        val expectedList = listOf(singleItem, singleItem)
        whenever(flowFiber.suspend<List<ByteBuffer>>(any())).thenReturn(listOf(byteBuffer, byteBuffer))
        whenever(serializationService.deserialize<TestObject>(any<ByteArray>(), any())).thenReturn(singleItem)

        val r = persistenceService.findAll(TestObject::class.java).execute()
        assertThat(r).isEqualTo(expectedList)

        verify(serializationService, times(2)).deserialize<TestObject>(any<ByteArray>(), any())
        verify(serializationService, times(0)).serialize<String>(any())
        assertThat(argumentCaptor.firstValue).isEqualTo(FindAllExternalEventFactory::class.java)
    }

    @Test
    fun `find all fails when deserializes to wrong type`() {
        whenever(flowFiber.suspend<List<ByteBuffer>>(any())).thenReturn(listOf(byteBuffer))
        whenever(serializationService.deserialize<FailTestObject>(any<ByteArray>(), any())).thenReturn(FailTestObject())

        assertThrows<CordaRuntimeException> { persistenceService.findAll(TestObject::class.java).execute() }

        verify(serializationService).deserialize<TestObject>(any<ByteArray>(), any())
        verify(serializationService, times(0)).serialize<String>(any())
        assertThat(argumentCaptor.firstValue).isEqualTo(FindAllExternalEventFactory::class.java)
    }

    class TestObject
    class FailTestObject
}