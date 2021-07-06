package net.corda.messaging.kafka.publisher

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import net.corda.schema.registry.AvroSchemaRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer

class CordaAvroSerializerTest {

    private val topic = "topic"
    private val avroSchemaRegistry : AvroSchemaRegistry = mock()
    private val cordaAvroSerializer = CordaAvroSerializer<Any>(avroSchemaRegistry)

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
}
