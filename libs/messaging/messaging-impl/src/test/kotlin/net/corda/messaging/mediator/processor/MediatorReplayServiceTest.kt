package net.corda.messaging.mediator.processor

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.crypto.cipher.suite.sha256Bytes
import net.corda.data.messaging.mediator.MediatorReplayOutputEvent
import net.corda.data.messaging.mediator.MediatorReplayOutputEventType
import net.corda.data.messaging.mediator.MediatorReplayOutputEvents
import net.corda.data.messaging.mediator.MediatorState
import net.corda.data.messaging.mediator.SerializationType
import net.corda.messaging.api.mediator.MediatorMessage
import net.corda.messaging.api.mediator.MessagingClient
import net.corda.messaging.api.mediator.MessagingClient.Companion.MSG_PROP_TOPIC
import net.corda.messaging.api.records.Record
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer

class MediatorReplayServiceTest {
    private val topic = "topic"
    private val testState = ByteBuffer.wrap("state".toByteArray())

    private lateinit var serializer: CordaAvroSerializer<Any>
    private lateinit var deserializer: CordaAvroDeserializer<Any>
    private lateinit var cordaAvroSerializationFactory: CordaAvroSerializationFactory
    private lateinit var mediatorReplayService: MediatorReplayService

    @BeforeEach
    fun setup() {
        serializer = mock<CordaAvroSerializer<Any>>().apply {
            whenever(serialize(any())).doAnswer{
                it.arguments[0].toString().toByteArray()
            }
        }
        deserializer = mock<CordaAvroDeserializer<Any>>().apply {
            whenever(deserialize(anyOrNull())).doAnswer {
                it.arguments[0].toString().toByteArray()
            }
        }

        cordaAvroSerializationFactory = mock<CordaAvroSerializationFactory>().apply {
            whenever(createAvroSerializer<Any>(anyOrNull())).thenReturn(serializer)
            whenever(createAvroDeserializer<Any>(anyOrNull(), any())).thenReturn(deserializer)
        }
        mediatorReplayService = MediatorReplayService(cordaAvroSerializationFactory)
    }

    @Test
    fun `Add new output events to empty mediator state`() {
        val mediatorReplayOutputEvents = mediatorReplayOutputEvents()
        val numberOfKeys = 2
        val numberOfValues = 4
        val outputs = mediatorReplayService.getOutputEvents(
            mediatorReplayOutputEvents,
            getNewOutputs(numberOfKeys, numberOfValues)
        )

        assertEquals(2, outputs.size)
        outputs.onEach {
            assertEquals(4, it.outputEvents.size)
        }
    }

    @Test
    fun `Add new output events to mediator state with existing outputs with one new key and 3 existing keys`() {
        val mediatorReplayOutputEvents = mediatorReplayOutputEvents(3, 3)
        val numberOfKeys = 4
        val numberOfValues = 1
        val outputs = mediatorReplayService.getOutputEvents(
            mediatorReplayOutputEvents,
            getNewOutputs(numberOfKeys, numberOfValues)
        )
        assertEquals(7, outputs.size)
        assertEquals(13, outputs.sumOf { it.outputEvents.size })
    }

    @Test
    fun `Add new output events to mediator state with existing outputs same with same keys`() {
        val mediatorReplayOutputEvents = mediatorReplayOutputEvents(3, 3)
        val numberOfKeys = 2
        val numberOfValues = 4
        val outputs = mediatorReplayService.getOutputEvents(
            mediatorReplayOutputEvents,
            getNewOutputs(numberOfKeys, numberOfValues)
        )
        assertEquals(5, outputs.size)
        assertEquals(17, outputs.sumOf { it.outputEvents.size })
    }

    @Test
    fun `Add new output events with missing mandatory properties throws exception`() {
        val mediatorReplayOutputEvents = mediatorReplayOutputEvents()
        val numberOfKeys = 2
        val numberOfValues = 4
        assertThrows<IllegalStateException> {
            mediatorReplayService.getOutputEvents(
                mediatorReplayOutputEvents,
                getNewOutputs(numberOfKeys, numberOfValues, true)
            )
        }
    }

    @Test
    fun `input record is replay event`() {
        val record = Record(topic, "1", "1")
        val inputRecords = listOf(record)
        val existingOutputs = mediatorReplayOutputEvents(2, 3)
        val outputs  = mediatorReplayService.getReplayEvents(inputRecords, MediatorState(testState, existingOutputs))
        assertEquals(1, outputs.size)
        assertEquals(3, outputs.first().outputs.size)
    }

    @Test
    fun `input record is not a replay event, empty outputs`() {
        val inputRecords = listOf(Record(topic, "test1", "test1"))
        assertEquals(0, mediatorReplayService.getReplayEvents(inputRecords, MediatorState(testState, mutableListOf())).size)
    }

    @Test
    fun `input record is not a replay event, existing outputs`() {
        val existingOutputs = mediatorReplayOutputEvents(2, 3)
        val inputRecords = listOf(Record(topic, "test3", "test3"))
        whenever(serializer.serialize(any())).thenReturn("bytes".toByteArray())
        assertEquals(0, mediatorReplayService.getReplayEvents(inputRecords, MediatorState(testState, existingOutputs)).size)
    }

    private fun mediatorReplayOutputEvents(existingKeys: Int = 0, existingValuesPerKey: Int = 0): List<MediatorReplayOutputEvents> {
        if (existingKeys == 0)  return mutableListOf()

        val existingOutputs = mutableListOf<MediatorReplayOutputEvents>()
        for (i in 1 .. existingKeys) {
            val recordKey = "$i"
            val outputsPerKey = mutableListOf<MediatorReplayOutputEvent>()
            for (j in 1 .. existingValuesPerKey) {
                outputsPerKey.add(
                    MediatorReplayOutputEvent(
                        topic,
                        MediatorReplayOutputEventType(ByteBuffer.wrap(recordKey.toByteArray()), SerializationType.BYTEARRAY),
                        MediatorReplayOutputEventType(ByteBuffer.wrap("$j".toByteArray()), SerializationType.BYTEARRAY),
                    )
                )
            }
            val hash = ByteBuffer.wrap("$i".toByteArray().sha256Bytes())
            existingOutputs.add(MediatorReplayOutputEvents(hash, outputsPerKey))
        }
        return existingOutputs
    }

    private fun getNewOutputs(
        numberOfKeys: Int,
        numberOfRecordsPerKey: Int,
        missingProperty: Boolean = false
    ): Map<ByteBuffer, MutableList<MediatorMessage<Any>>> {
        val newOutputs = mutableMapOf<ByteBuffer, MutableList<MediatorMessage<Any>>>()
        for (consumerInputKey in 1 .. numberOfKeys) {
            val recordKey = consumerInputKey.toString()
            val outputsPerKey = mutableListOf<MediatorMessage<Any>>()
            for (outputPayload in 1 .. numberOfRecordsPerKey) {
                outputsPerKey.add(MediatorMessage("$outputPayload", getProperties(recordKey, missingProperty)))
            }
            newOutputs[ByteBuffer.wrap(recordKey.toByteArray())] = outputsPerKey
        }

        return newOutputs
    }

    private fun getProperties(key: String, missingProperty: Boolean): MutableMap<String, Any> {
        val testProperties: MutableMap<String, Any> = mutableMapOf(MessagingClient.MSG_PROP_KEY to key)
        if (!missingProperty) {
            testProperties[MSG_PROP_TOPIC] = topic
        }
        return testProperties
    }
}