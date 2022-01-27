package net.corda.messagebus.kafka.serialization

import net.corda.schema.registry.AvroSchemaRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer

class CordaAvroSerializerImplTest {

    private val topic = "topic"
    private val avroSchemaRegistry : AvroSchemaRegistry = mock()
    private val cordaAvroSerializer = CordaAvroSerializerImpl<Any>(avroSchemaRegistry)

    @BeforeEach
    fun setup () {
        whenever(avroSchemaRegistry.serialize(any())).thenReturn(ByteBuffer.wrap("bytes".toByteArray()))
    }

    @Test
    fun testNotNullValue() {
        assertThat(cordaAvroSerializer.serialize(topic, Any()) != null)
    }

    @Test
    fun testNullValue() {
        assertThat(cordaAvroSerializer.serialize(topic, null) == null)
    }

    @Test
    fun testStringValue() {
        assertThat(cordaAvroSerializer.serialize(topic, "string") != null)
    }

    @Test
    fun testByteArrayValue() {
        assertThat(cordaAvroSerializer.serialize(topic, "bytearray".toByteArray()) != null)
    }
}
