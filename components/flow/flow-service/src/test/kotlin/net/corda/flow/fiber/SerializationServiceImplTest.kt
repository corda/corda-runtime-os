package net.corda.flow.fiber

import net.corda.flow.application.serialization.SerializationServiceImpl
import net.corda.flow.pipeline.sandbox.FlowSandboxGroupContext
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.sandboxgroupcontext.RequireSandboxAMQP.AMQP_SERIALIZATION_SERVICE
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

class SerializationServiceImplTest {

    private class TestObject
    private val currentSandboxGroupContext : CurrentSandboxGroupContext = mock()

    private val sandboxGroupContext = mock<FlowSandboxGroupContext>()
    private val serializationService = mock<SerializationService>()
    private val serializedBytes = mock<SerializedBytes<Any>>()

    private val byteArray = "bytes".toByteArray()


    private val flowFiberSerializationService = SerializationServiceImpl(currentSandboxGroupContext)

    @BeforeEach
    fun setup() {
        whenever(currentSandboxGroupContext.get()).thenReturn(sandboxGroupContext)
        whenever(sandboxGroupContext.get(AMQP_SERIALIZATION_SERVICE, SerializationService::class.java)).thenReturn(serializationService)
        whenever(serializationService.serialize(any())).thenReturn(serializedBytes)
    }

    @Test
    fun `deserialize success`() {
        val tesObj = TestObject()
        whenever(serializationService.deserialize(byteArray, TestObject::class.java)).thenReturn(tesObj)

        val deserialized = flowFiberSerializationService.deserialize(byteArray, TestObject::class.java)

        assertThat(deserialized).isEqualTo(tesObj)
        verify(serializationService, times(1)).deserialize(byteArray,  TestObject::class.java)
        verify(currentSandboxGroupContext, times(1)).get()
    }

    @Test
    fun `deserializeAndCheckType success`() {
        val tesObj = TestObject()
        whenever(serializationService.deserialize(byteArray, TestObject::class.java)).thenReturn(tesObj)

        val deserialized = flowFiberSerializationService.deserializeAndCheckType(byteArray, TestObject::class.java)

        assertThat(deserialized).isEqualTo(tesObj)
        verify(serializationService, times(1)).deserialize(byteArray,  TestObject::class.java)
        verify(currentSandboxGroupContext, times(1)).get()
    }

    @Test
    fun `deserializeAndCheckType wrong object`() {
        whenever(serializationService.deserialize<Any>(any<ByteArray>(),  any())).thenReturn(1)

        assertThrows<CordaRuntimeException> { flowFiberSerializationService.deserializeAndCheckType(byteArray, TestObject::class.java) }

        verify(serializationService, times(1)).deserialize(byteArray,  TestObject::class.java)
        verify(currentSandboxGroupContext, times(1)).get()
    }

    @Test
    fun `serialize success`() {
        val testObj = TestObject()
        val deserialized = flowFiberSerializationService.serialize(testObj)

        assertThat(deserialized).isEqualTo(serializedBytes)
        verify(serializationService, times(1)).serialize(testObj)
        verify(currentSandboxGroupContext, times(1)).get()
    }
}