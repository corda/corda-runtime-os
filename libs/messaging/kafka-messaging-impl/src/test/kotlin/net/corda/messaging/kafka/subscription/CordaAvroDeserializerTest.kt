package net.corda.messaging.kafka.subscription

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.anyOrNull
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyZeroInteractions
import com.nhaarman.mockito_kotlin.whenever
import net.corda.messaging.kafka.publisher.CordaAvroSerializer
import net.corda.messaging.kafka.publisher.CordaAvroSerializer.Companion
import net.corda.messaging.kafka.publisher.CordaAvroSerializer.Companion.BYTE_ARRAY_MAGIC
import net.corda.messaging.kafka.publisher.CordaAvroSerializer.Companion.STRING_MAGIC
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class CordaAvroDeserializerTest {

    private companion object {
        val kafkaSerializer = StringSerializer()
    }
    @Test
    fun `simple string deserialize test`() {
        val schemaRegistry: AvroSchemaRegistry = mock()
        val callback: (String, ByteArray) -> Unit = mock()
        val deserializer = CordaAvroDeserializer<String>( mock(),  mock())
        val win = deserializer.deserialize("", kafkaSerializer.serialize("",STRING_MAGIC) + kafkaSerializer.serialize("","Win!"))

        assertThat(win).isEqualTo("Win!")
        verifyZeroInteractions(schemaRegistry)
        verifyZeroInteractions(callback)
    }

    @Test
    fun `simple byte array deserialize test`() {
        val schemaRegistry: AvroSchemaRegistry = mock()
        val callback: (String, ByteArray) -> Unit = mock()
        val deserializer = CordaAvroDeserializer<ByteArray>( mock(),  mock())
        val win = deserializer.deserialize("", BYTE_ARRAY_MAGIC.toByteArray() + "Win!".toByteArray())

        assertThat(win).isEqualTo("Win!".toByteArray())
        verifyZeroInteractions(schemaRegistry)
        verifyZeroInteractions(callback)
    }

    @Test
    fun `complex type deserialize test`() {
        val schemaRegistry: AvroSchemaRegistry = mock<AvroSchemaRegistry>().also {
            whenever(it.deserialize(any(), any(), anyOrNull())).thenReturn("Win!")
            whenever(it.getClassType(any())).thenReturn(String::class.java)
        }
        val callback: (String, ByteArray) -> Unit = mock()
        val deserializer = CordaAvroDeserializer<String>(schemaRegistry, callback)
        val win = deserializer.deserialize("", ByteArray(1))

        assertThat(win).isEqualTo("Win!")
        verifyZeroInteractions(callback)
    }

    @Test
    fun `callback is executed when deserialize fails`() {
        val schemaRegistry: AvroSchemaRegistry = mock<AvroSchemaRegistry>().also {
            whenever(it.deserialize(any(), any(), anyOrNull())).thenThrow(CordaRuntimeException(""))
            whenever(it.getClassType(any())).thenReturn(String::class.java)
        }
        val callback: (String, ByteArray) -> Unit = mock()
        val deserializer = CordaAvroDeserializer<String>(schemaRegistry, callback)
        val topic = "topic"
        val data = ByteArray(10)
        val win = deserializer.deserialize(topic, data)

        assertThat(win).isNull()
        verify(callback, times(1)).invoke(eq("topic"), eq(data))
    }
}
