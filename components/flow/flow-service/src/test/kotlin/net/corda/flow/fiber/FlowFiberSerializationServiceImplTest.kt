package net.corda.flow.fiber

import net.corda.flow.pipeline.sandbox.FlowSandboxGroupContext
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

class FlowFiberSerializationServiceImplTest {

    private class TestObject
    private val flowFiberService : FlowFiberService = mock()

    private val flowFiber = mock<FlowFiber>()
    private val executionContext = mock<FlowFiberExecutionContext>()
    private val sandboxGroupContext = mock<FlowSandboxGroupContext>()
    private val serializationService = mock<SerializationService>()
    private val serializedBytes = mock<SerializedBytes<Any>>()

    private val byteArray = "bytes".toByteArray()


    private val flowFiberSerializationService = FlowFiberSerializationServiceImpl(flowFiberService)

    @BeforeEach
    fun setup() {
        whenever(flowFiberService.getExecutingFiber()).thenReturn(flowFiber)
        whenever(flowFiber.getExecutionContext()).thenReturn(executionContext)
        whenever(executionContext.sandboxGroupContext).thenReturn(sandboxGroupContext)
        whenever(sandboxGroupContext.amqpSerializer).thenReturn(serializationService)
        whenever(serializationService.serialize(any())).thenReturn(serializedBytes)
    }

    @Test
    fun `deserialize success`() {
        val tesObj = TestObject()
        whenever(serializationService.deserialize(byteArray, TestObject::class.java)).thenReturn(tesObj)

        val deserialized = flowFiberSerializationService.deserialize(byteArray, TestObject::class.java)

        assertThat(deserialized).isEqualTo(tesObj)
        verify(serializationService, times(1)).deserialize(byteArray,  TestObject::class.java)
        verify(flowFiberService, times(1)).getExecutingFiber()
    }

    @Test
    fun `deserialize wrong object`() {
        whenever(serializationService.deserialize<Any>(any<ByteArray>(),  any())).thenReturn(1)

        assertThrows<CordaRuntimeException> { flowFiberSerializationService.deserialize(byteArray, TestObject::class.java) }

        verify(serializationService, times(1)).deserialize(byteArray,  TestObject::class.java)
        verify(flowFiberService, times(1)).getExecutingFiber()
    }

    @Test
    fun `serialize success`() {
        val testObj = TestObject()
        val deserialized = flowFiberSerializationService.serialize(testObj)

        assertThat(deserialized).isEqualTo(serializedBytes)
        verify(serializationService, times(1)).serialize(testObj)
        verify(flowFiberService, times(1)).getExecutingFiber()
    }
}