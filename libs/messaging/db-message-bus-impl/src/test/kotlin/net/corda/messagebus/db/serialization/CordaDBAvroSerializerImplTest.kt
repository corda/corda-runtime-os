package net.corda.messagebus.db.serialization

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
import java.nio.ByteBuffer

class CordaDBAvroSerializerImplTest {

    private val avroSchemaRegistry: AvroSchemaRegistry = mock()
    private val cordaDBAvroSerializer = CordaDBAvroSerializerImpl<Any>(avroSchemaRegistry, null)

    data class SerializeTester(val contents: String = "test contents")

    @BeforeEach
    fun setup() {
        whenever(avroSchemaRegistry.serialize(any())).thenReturn(ByteBuffer.wrap("bytes".toByteArray()))
    }

    @Test
    fun testNotNullValue() {
        assertThat(cordaDBAvroSerializer.serialize(Any()) != null)
    }

    @Test
    fun testStringValue() {
        assertThat(cordaDBAvroSerializer.serialize("data") != null)
    }

    @Test
    fun testByteArrayValue() {
        assertThat(cordaDBAvroSerializer.serialize("data".toByteArray()) != null)
    }

    @Test
    fun testCustomClassValue() {
        assertThat(cordaDBAvroSerializer.serialize(SerializeTester()) != null)
    }

    @Test
    fun testExceptionPropagation() {
        val data = SerializeTester()
        whenever(avroSchemaRegistry.serialize(data)).thenThrow(IllegalStateException())
        assertThrows<CordaRuntimeException> { cordaDBAvroSerializer.serialize(data) }
    }

    @Test
    fun testThrowOnError() {
        var hasRan = false
        val data = SerializeTester()
        whenever(avroSchemaRegistry.serialize(data)).thenThrow(IllegalStateException())

        val onErrorSerializer = CordaDBAvroSerializerImpl<Any>(avroSchemaRegistry) { hasRan = true }
        assertThrows<CordaRuntimeException> { onErrorSerializer.serialize(data) }
        assertTrue(hasRan)
    }
}
