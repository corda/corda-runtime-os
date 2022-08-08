package net.corda.flow.application.persistence

import java.nio.ByteBuffer
import net.corda.data.persistence.DeleteEntity
import net.corda.data.persistence.FindAll
import net.corda.data.persistence.FindEntity
import net.corda.data.persistence.MergeEntity
import net.corda.data.persistence.PersistEntity
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
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
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

    private val argumentCaptor = argumentCaptor<PersistenceParameters>()

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
                eq(PersistenceServiceExternalEventFactory::class.java),
                argumentCaptor.capture()
            )
        ).thenReturn(byteBuffer.array())
    }

    @Test
    fun `persist executes`() {
        persistenceService.persist(TestObject())

        verify(serializationService).serialize(any())
        assertThat(argumentCaptor.firstValue.request).isInstanceOf(PersistEntity::class.java)
        assertInstanceOf(PersistEntity::class.java, argumentCaptor.firstValue.request)
    }

    @Test
    fun `merge executes successfully`() {
        whenever(flowFiber.suspend<ByteBuffer?>(any())).thenReturn(byteBuffer)
        whenever(serializationService.deserialize<TestObject>(any<ByteArray>(), any())).thenReturn(TestObject())

        persistenceService.merge(TestObject())

        verify(serializationService).deserialize<TestObject>(any<ByteArray>(), any())
        verify(serializationService).serialize<TestObject>(any())
        assertThat(argumentCaptor.firstValue.request).isInstanceOf(MergeEntity::class.java)
        assertInstanceOf(MergeEntity::class.java, argumentCaptor.firstValue.request)
    }

    @Test
    fun `merge fails when deserializes to wrong type`() {
        whenever(flowFiber.suspend<ByteBuffer?>(any())).thenReturn(byteBuffer)
        whenever(serializationService.deserialize<Any>(any<ByteArray>(), any())).thenReturn(FailTestObject())

        assertThrows<CordaRuntimeException> { persistenceService.merge(TestObject()) }

        verify(serializationService).deserialize<TestObject>(any<ByteArray>(), any())
        verify(serializationService).serialize<TestObject>(any())
        assertInstanceOf(MergeEntity::class.java, argumentCaptor.firstValue.request)
    }

    @Test
    fun `remove executes successfully`() {
        whenever(flowFiber.suspend<ByteBuffer?>(any())).thenReturn(byteBuffer)
        whenever(serializationService.deserialize<TestObject>(any<ByteArray>(), any())).thenReturn(TestObject())

        persistenceService.remove(TestObject())

        verify(serializationService, times(0)).deserialize<TestObject>(any<ByteArray>(), any())
        verify(serializationService).serialize<TestObject>(any())
        assertInstanceOf(DeleteEntity::class.java, argumentCaptor.firstValue.request)
    }

    @Test
    fun `find executes successfully`() {
        val expectedObj = TestObject()
        whenever(flowFiber.suspend<ByteBuffer?>(any())).thenReturn(byteBuffer)
        whenever(serializationService.deserialize<TestObject>(any<ByteArray>(), any())).thenReturn(expectedObj)

        assertThat(persistenceService.find(TestObject::class.java, "key")).isEqualTo(expectedObj)

        verify(serializationService).deserialize<TestObject>(any<ByteArray>(), any())
        verify(serializationService).serialize<String>(any())
        assertInstanceOf(FindEntity::class.java, argumentCaptor.firstValue.request)
    }

    @Test
    fun `find fails when deserializes to wrong type`() {
        whenever(flowFiber.suspend<ByteBuffer?>(any())).thenReturn(byteBuffer)
        whenever(serializationService.deserialize<Any>(any<ByteArray>(), any())).thenReturn(FailTestObject())

        assertThrows<CordaRuntimeException> { persistenceService.find(TestObject::class.java, "key") }

        verify(serializationService).deserialize<TestObject>(any<ByteArray>(), any())
        verify(serializationService).serialize<String>(any())
        assertInstanceOf(FindEntity::class.java, argumentCaptor.firstValue.request)
    }

    @Test
    fun `find all executes successfully`() {
        val expectedList = listOf(TestObject(), TestObject())
        whenever(flowFiber.suspend<ByteBuffer?>(any())).thenReturn(byteBuffer)
        whenever(serializationService.deserialize<List<TestObject>>(any<ByteArray>(), any())).thenReturn(expectedList)

        assertThat(persistenceService.findAll(TestObject::class.java).execute()).isEqualTo(expectedList)

        verify(serializationService).deserialize<TestObject>(any<ByteArray>(), any())
        verify(serializationService, times(0)).serialize<String>(any())
        assertInstanceOf(FindAll::class.java, argumentCaptor.firstValue.request)
    }

    @Test
    fun `find all fails when deserializes to wrong type`() {
        whenever(flowFiber.suspend<ByteBuffer?>(any())).thenReturn(byteBuffer)
        whenever(serializationService.deserialize<FailTestObject>(any<ByteArray>(), any())).thenReturn(FailTestObject())

        assertThrows<CordaRuntimeException> { persistenceService.findAll(TestObject::class.java).execute() }

        verify(serializationService).deserialize<TestObject>(any<ByteArray>(), any())
        verify(serializationService, times(0)).serialize<String>(any())
        assertInstanceOf(FindAll::class.java, argumentCaptor.firstValue.request)
    }

    class TestObject
    class FailTestObject
}