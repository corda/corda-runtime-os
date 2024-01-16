package net.corda.messaging.mediator.processor

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.data.messaging.mediator.MediatorReplayOutputEvent
import net.corda.data.messaging.mediator.MediatorReplayOutputEvents
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
import java.util.UUID

class MediatorReplayServiceTest {

    private lateinit var serializer: CordaAvroSerializer<Any>
    private lateinit var cordaAvroSerializationFactory: CordaAvroSerializationFactory
    private lateinit var mediatorReplayService: MediatorReplayService

    @BeforeEach
    fun setup() {
        serializer = mock<CordaAvroSerializer<Any>>().apply {
            whenever(serialize(anyOrNull())).doAnswer{ UUID.randomUUID().toString().toByteArray() }
        }
        cordaAvroSerializationFactory = mock<CordaAvroSerializationFactory>().apply {
            whenever(createAvroSerializer<Any>(anyOrNull())).thenReturn(serializer)
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
    fun `Add new output events to mediator state with existing outputs with the same key`() {
        whenever(serializer.serialize(any())).thenReturn("1".toByteArray())
        val mediatorReplayOutputEvents = mediatorReplayOutputEvents(3, 3)
        val numberOfKeys = 1
        val numberOfValues = 4
        val outputs = mediatorReplayService.getOutputEvents(
            mediatorReplayOutputEvents,
            getNewOutputs(numberOfKeys, numberOfValues)
        )
        assertEquals(4, outputs.size)
        assertEquals(13, outputs.sumOf { it.outputEvents.size })
    }

    @Test
    fun `Add new output events to mediator state with existing outputs`() {
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

    private fun mediatorReplayOutputEvents(existingKeys: Int = 0, existingValuesPerKey: Int = 0): List<MediatorReplayOutputEvents> {
        if (existingKeys == 0)  return mutableListOf()

        val existingOutputs = mutableListOf<MediatorReplayOutputEvents>()
        for (i in 1 .. existingKeys) {
            val recordKey = i.toString()
            val outputsPerKey = mutableListOf<MediatorReplayOutputEvent>()
            for (j in 1 .. existingValuesPerKey) {
                outputsPerKey.add(
                    MediatorReplayOutputEvent(
                        "topic",
                        ByteBuffer.wrap(recordKey.toByteArray()),
                        ByteBuffer.wrap(recordKey.toByteArray())
                    )
                )
            }
            existingOutputs.add(MediatorReplayOutputEvents(ByteBuffer.wrap("$i".toByteArray()), outputsPerKey))
        }
        return existingOutputs
    }

    private fun getNewOutputs(
        numberOfKeys: Int,
        numberOfRecordsPerKey: Int,
        missingProperty: Boolean = false
    ): Map<Record<String, String>, MutableList<MediatorMessage<Any>>> {
        val newOutputs = mutableMapOf<Record<String, String>, MutableList<MediatorMessage<Any>>>()
        for (i in 1 .. numberOfKeys) {
            val recordKey = i.toString()
            val outputsPerKey = mutableListOf<MediatorMessage<Any>>()
            for (j in 1 .. numberOfRecordsPerKey) {
                outputsPerKey.add(MediatorMessage("$j", getProperties(recordKey, missingProperty)))
            }
            newOutputs[Record("topic", recordKey, recordKey)] = outputsPerKey
        }

        return newOutputs
    }

    private fun getProperties(key: String, missingProperty: Boolean): MutableMap<String, Any> {
        val testProperties: MutableMap<String, Any> = mutableMapOf(MessagingClient.MSG_PROP_KEY to key)
        if (!missingProperty) {
            testProperties[MSG_PROP_TOPIC] = "topic"
        }
        return testProperties
    }
}