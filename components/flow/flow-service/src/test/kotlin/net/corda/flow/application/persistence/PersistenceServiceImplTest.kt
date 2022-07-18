package net.corda.flow.application.persistence

import java.nio.ByteBuffer
import net.corda.flow.fiber.FlowFiber
import net.corda.flow.fiber.FlowFiberExecutionContext
import net.corda.flow.fiber.FlowFiberService
import net.corda.flow.fiber.FlowIORequest
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PersistenceServiceImplTest {

    private lateinit var flowFiberService: FlowFiberService
    private lateinit var flowFiber: FlowFiber
    private lateinit var executionContext: FlowFiberExecutionContext
    private lateinit var sandboxGroupContext: FlowSandboxGroupContext
    private lateinit var serializationService: SerializationService
    private lateinit var serializedBytes: SerializedBytes<Any>

    private lateinit var persistenceService: PersistenceService

    private val byteBuffer = ByteBuffer.wrap("bytes".toByteArray())

    @BeforeEach
    fun setup() {
        flowFiberService = mock()
        persistenceService = PersistenceServiceImpl(flowFiberService)
        flowFiber = mock()
        executionContext = mock()
        sandboxGroupContext = mock()
        serializationService = mock()
        serializedBytes = mock()

        whenever(flowFiberService.getExecutingFiber()).thenReturn(flowFiber)
        whenever(flowFiber.getExecutionContext()).thenReturn(executionContext)
        whenever(executionContext.sandboxGroupContext).thenReturn(sandboxGroupContext)
        whenever(sandboxGroupContext.amqpSerializer).thenReturn(serializationService)
        whenever(serializationService.serialize(any())).thenReturn(serializedBytes)
        whenever(serializedBytes.bytes).thenReturn(byteBuffer.array())
    }

    @Test
    fun `Test persist executes`() {
        persistenceService.persist(TestObject())

        verify(serializationService, times(1)).serialize(any())
        verify(flowFiber, times(1)).suspend(any<FlowIORequest.Persist>())
    }

    @Test
    fun `Test merge executes successfully`() {
        whenever(flowFiber.suspend<ByteBuffer?>(any())).thenReturn(byteBuffer)
        whenever(serializationService.deserialize<TestObject>(any<ByteArray>(), any())).thenReturn(TestObject())

        persistenceService.merge(TestObject())

        verify(serializationService, times(1)).deserialize<TestObject>(any<ByteArray>(), any())
        verify(serializationService, times(1)).serialize<TestObject>(any())
        verify(flowFiber, times(1)).suspend(any<FlowIORequest.Merge>())
    }

    @Test
    fun `Test merge fails when deserializes to wrong type`() {
        whenever(flowFiber.suspend<ByteBuffer?>(any())).thenReturn(byteBuffer)
        whenever(serializationService.deserialize<Any>(any<ByteArray>(), any())).thenReturn(FailTestObject())

        assertThrows<CordaRuntimeException> { persistenceService.merge(TestObject()) }

        verify(serializationService, times(1)).deserialize<TestObject>(any<ByteArray>(), any())
        verify(serializationService, times(1)).serialize<TestObject>(any())
        verify(flowFiber, times(1)).suspend(any<FlowIORequest.Merge>())
    }

    @Test
    fun `Test remove executes successfully`() {
        whenever(flowFiber.suspend<ByteBuffer?>(any())).thenReturn(byteBuffer)
        whenever(serializationService.deserialize<TestObject>(any<ByteArray>(), any())).thenReturn(TestObject())

        persistenceService.remove(TestObject())

        verify(serializationService, times(0)).deserialize<TestObject>(any<ByteArray>(), any())
        verify(serializationService, times(1)).serialize<TestObject>(any())
        verify(flowFiber, times(1)).suspend(any<FlowIORequest.Delete>())
    }

    @Test
    fun `Test find executes successfully`() {
        val expectedObj = TestObject()
        whenever(flowFiber.suspend<ByteBuffer?>(any())).thenReturn(byteBuffer)
        whenever(serializationService.deserialize<TestObject>(any<ByteArray>(), any())).thenReturn(expectedObj)

        assertThat(persistenceService.find(TestObject::class.java, "key")).isEqualTo(expectedObj)

        verify(serializationService, times(1)).deserialize<TestObject>(any<ByteArray>(), any())
        verify(serializationService, times(1)).serialize<String>(any())
        verify(flowFiber, times(1)).suspend(any<FlowIORequest.Find>())
    }

    @Test
    fun `Test find fails when deserializes to wrong type`() {
        whenever(flowFiber.suspend<ByteBuffer?>(any())).thenReturn(byteBuffer)
        whenever(serializationService.deserialize<Any>(any<ByteArray>(), any())).thenReturn(FailTestObject())

        assertThrows<CordaRuntimeException> { persistenceService.find(TestObject::class.java, "key") }

        verify(serializationService, times(1)).deserialize<TestObject>(any<ByteArray>(), any())
        verify(serializationService, times(1)).serialize<String>(any())
        verify(flowFiber, times(1)).suspend(any<FlowIORequest.Find>())
    }

    @Test
    fun `Test find all executes successfully`() {
        val expectedList = listOf(TestObject(),TestObject())
        whenever(flowFiber.suspend<ByteBuffer?>(any())).thenReturn(byteBuffer)
        whenever(serializationService.deserialize<List<TestObject>>(any<ByteArray>(), any())).thenReturn(expectedList)

        assertThat(persistenceService.findAll(TestObject::class.java)).isEqualTo(expectedList)

        verify(serializationService, times(1)).deserialize<TestObject>(any<ByteArray>(), any())
        verify(serializationService, times(0)).serialize<String>(any())
        verify(flowFiber, times(1)).suspend(any<FlowIORequest.FindAll>())
    }

    @Test
    fun `Test find all fails when deserializes to wrong type`() {
        whenever(flowFiber.suspend<ByteBuffer?>(any())).thenReturn(byteBuffer)
        whenever(serializationService.deserialize<FailTestObject>(any<ByteArray>(), any())).thenReturn(FailTestObject())

        assertThrows<CordaRuntimeException> { persistenceService.findAll(TestObject::class.java) }

        verify(serializationService, times(1)).deserialize<TestObject>(any<ByteArray>(), any())
        verify(serializationService, times(0)).serialize<String>(any())
        verify(flowFiber, times(1)).suspend(any<FlowIORequest.FindAll>())
    }

    class TestObject
    class FailTestObject
}