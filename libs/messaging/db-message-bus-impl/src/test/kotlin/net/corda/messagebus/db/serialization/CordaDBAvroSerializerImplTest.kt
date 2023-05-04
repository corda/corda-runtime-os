package net.corda.messagebus.db.serialization

import java.nio.ByteBuffer
import net.corda.schema.registry.AvroSchemaRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class CordaDBAvroSerializerImplTest {

    private val avroSchemaRegistry: AvroSchemaRegistry = mock()
    private val cordaDBAvroSerializer = CordaDBAvroSerializerImpl<Any>(avroSchemaRegistry, true, null)

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
        assertThrows<IllegalStateException> { cordaDBAvroSerializer.serialize(data) }
    }

    @Test
    fun testThrowOnError() {
        var hasRan = false
        val data = SerializeTester()
        whenever(avroSchemaRegistry.serialize(data)).thenThrow(IllegalStateException())

        val onErrorSerializer = CordaDBAvroSerializerImpl<Any>(avroSchemaRegistry, true) { hasRan = true }
        assertThrows<IllegalStateException> { onErrorSerializer.serialize(data) }
        assertTrue(hasRan)
    }

    @Test
    fun testThrowOnFailureIsFalse() {
        val data = SerializeTester()
        whenever(avroSchemaRegistry.serialize(data)).thenThrow(IllegalStateException())

        val serializer = CordaDBAvroSerializerImpl<Any>(avroSchemaRegistry, false, null)
        assertDoesNotThrow {
            assertNull(serializer.serialize(data))
        }
    }

}