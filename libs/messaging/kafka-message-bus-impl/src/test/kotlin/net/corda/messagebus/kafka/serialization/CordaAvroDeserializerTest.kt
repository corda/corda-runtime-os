package net.corda.messaging.kafka.subscription.net.corda.messagebus.kafka.serialization

import net.corda.data.crypto.SecureHash
import net.corda.messagebus.kafka.serialization.CordaAvroDeserializerImpl
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.apache.kafka.common.serialization.StringSerializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer

internal class CordaAvroDeserializerTest {

    private companion object {
        val kafkaSerializer = StringSerializer()
    }
    @Test
    fun `simple string deserialize test`() {
        val schemaRegistry: AvroSchemaRegistry = mock()
        val callback: (String, ByteArray) -> Unit = mock()
        val deserializer = CordaAvroDeserializerImpl( mock(),  mock(), String::class.java)
        val win = deserializer.deserialize("", kafkaSerializer.serialize("","Win!"))

        assertThat(win).isEqualTo("Win!")
        verifyNoInteractions(schemaRegistry)
        verifyNoInteractions(callback)
    }

    @Test
    fun `simple byte array deserialize test`() {
        val schemaRegistry: AvroSchemaRegistry = mock()
        val callback: (String, ByteArray) -> Unit = mock()
        val deserializer = CordaAvroDeserializerImpl( mock(),  mock(), ByteArray::class.java)
        val win = deserializer.deserialize("", "Win!".toByteArray())

        assertThat(win).isEqualTo("Win!".toByteArray())
        verifyNoInteractions(schemaRegistry)
        verifyNoInteractions(callback)
    }

    @Test
    fun `complex type deserialize test`() {

        val secureHash = SecureHash("algorithm", ByteBuffer.wrap("1".toByteArray()))
        val schemaRegistry: AvroSchemaRegistry = mock<AvroSchemaRegistry>().also {
            whenever(it.deserialize(any(), any(), anyOrNull())).thenReturn(secureHash)
            whenever(it.getClassType(any())).thenReturn(SecureHash::class.java)
        }
        val callback: (ByteArray) -> Unit = mock()
        val deserializer = CordaAvroDeserializerImpl(schemaRegistry, callback, SecureHash::class.java)
        val win = deserializer.deserialize("", ByteArray(1))

        assertThat(win).isEqualTo(secureHash)
        verifyNoInteractions(callback)
    }

    @Test
    fun `callback is executed when deserialize fails`() {
        val schemaRegistry: AvroSchemaRegistry = mock<AvroSchemaRegistry>().also {
            whenever(it.deserialize(any(), any(), anyOrNull())).thenThrow(CordaRuntimeException(""))
            whenever(it.getClassType(any())).thenReturn(SecureHash::class.java)
        }

        val callback: (ByteArray) -> Unit = mock()
        val deserializer = CordaAvroDeserializerImpl(schemaRegistry, callback, SecureHash::class.java)
        val topic = "topic"
        val data = ByteArray(10)
        val win = deserializer.deserialize(topic, data)

        assertThat(win).isNull()
        verify(callback, times(1)).invoke(eq(data))
    }
}
