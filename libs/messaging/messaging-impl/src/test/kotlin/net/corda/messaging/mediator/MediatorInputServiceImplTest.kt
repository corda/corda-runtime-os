package net.corda.messaging.mediator

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals

class MediatorInputServiceImplTest {
    private val serializer: CordaAvroSerializer<Any> = mock<CordaAvroSerializer<Any>>().apply {
        whenever(serialize(any())).thenReturn("bytes".toByteArray())
    }
    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory = mock<CordaAvroSerializationFactory>().apply {
        whenever(createAvroSerializer<Any>(anyOrNull())).thenReturn(serializer)
    }

    private val mediatorInputService = MediatorInputServiceImpl(cordaAvroSerializationFactory)

    @Test
    fun `get hash of valid input` () {
        val hash1 = mediatorInputService.getHash(CordaConsumerRecord(topic = "",0,0, key = "key", value = "value", 0))
        val hash2 = mediatorInputService.getHash(CordaConsumerRecord(topic = "",0,0, key = "key", value = "value", 0))
        assertEquals(hash1, hash2)
    }

    @Test
    fun `get hash of invalid input` () {
        whenever(serializer.serialize(any())).thenReturn(null)
        assertThrows<IllegalStateException> {
            mediatorInputService.getHash(CordaConsumerRecord(topic = "", 0, 0, key = "key", value = "value", 0))
        }
    }
}
