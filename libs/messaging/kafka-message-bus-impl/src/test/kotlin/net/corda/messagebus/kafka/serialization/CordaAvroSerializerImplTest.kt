package net.corda.messagebus.kafka.serialization

import java.nio.ByteBuffer
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class CordaAvroSerializerImplTest {

    private val avroSchemaRegistry: AvroSchemaRegistry = mock()
    private val cordaAvroSerializer = CordaAvroSerializerImpl<Any>(avroSchemaRegistry, null)

    data class SerializeTester(val contents: String = "test contents")

    @BeforeEach
    fun setup() {
        whenever(avroSchemaRegistry.serialize(any())).thenReturn(ByteBuffer.wrap("bytes".toByteArray()))
    }

    @Test
    fun testNotNullValue() {
        assertThat(cordaAvroSerializer.serialize(Any()) != null)
    }

    @Test
    fun testNullValue() {
        assertThat(cordaAvroSerializer.serialize("") == null)
    }

    @Test
    fun testStringValue() {
        assertThat(cordaAvroSerializer.serialize("string") != null)
    }

    @Test
    fun testByteArrayValue() {
        assertThat(cordaAvroSerializer.serialize("bytearray".toByteArray()) != null)
    }

    @Test
    fun testCustomClassValue() {
        assertThat(cordaAvroSerializer.serialize(SerializeTester()) != null)
    }

    @Test
    fun testExceptionPropagation() {
        val data = SerializeTester()
        whenever(avroSchemaRegistry.serialize(data)).thenThrow(IllegalStateException())
        assertThrows<CordaRuntimeException> { cordaAvroSerializer.serialize(data) }
    }

    @Test
    fun testThrowOnError() {
        var hasRan = false
        val data = SerializeTester()
        whenever(avroSchemaRegistry.serialize(data)).thenThrow(IllegalStateException())

        val onErrorSerializer = CordaAvroSerializerImpl<Any>(avroSchemaRegistry) { hasRan = true }
        assertThrows<CordaRuntimeException> { onErrorSerializer.serialize(data) }
        assertTrue(hasRan)
    }

}
