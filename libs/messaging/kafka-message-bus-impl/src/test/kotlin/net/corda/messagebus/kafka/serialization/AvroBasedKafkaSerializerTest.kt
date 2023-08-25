package net.corda.messaging.kafka.subscription.net.corda.messagebus.kafka.serialization

import net.corda.messagebus.kafka.serialization.CordaAvroSerializerImpl
import net.corda.messagebus.kafka.serialization.CordaKafkaSerializationFactoryImpl
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


class AvroBasedKafkaSerializerTest {
    private val topic = "topic"
    private val avroSchemaRegistry: AvroSchemaRegistry = mock()
    private val serializer = CordaKafkaSerializationFactoryImpl(avroSchemaRegistry).createAvroBasedKafkaSerializer(null)

    data class SerializeTester(val contents: String = "test contents")

    @BeforeEach
    fun setup() {
        whenever(avroSchemaRegistry.serialize(any())).thenReturn(ByteBuffer.wrap("bytes".toByteArray()))
    }

    @Test
    fun testNotNullValue() {
        assertThat(serializer(topic, Any()) != null)
    }

    @Test
    fun testNullValue() {
        assertThat(serializer(topic, null) == null)
    }

    @Test
    fun testStringValue() {
        assertThat(serializer(topic, "string") != null)
    }

    @Test
    fun testByteArrayValue() {
        assertThat(serializer(topic, "bytearray".toByteArray()) != null)
    }

    @Test
    fun testCustomClassValue() {
        assertThat(serializer(topic, SerializeTester()) != null)
    }

    @Test
    fun testExceptionPropagation() {
        val data = SerializeTester()
        whenever(avroSchemaRegistry.serialize(data)).thenThrow(IllegalStateException())
        assertThrows<CordaRuntimeException> { serializer(topic, data) }
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