package net.corda.messaging.mediator.processor

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.crypto.cipher.suite.sha256Bytes
import net.corda.data.messaging.mediator.MediatorReplayOutputEvent
import net.corda.data.messaging.mediator.MediatorReplayOutputEventType
import net.corda.data.messaging.mediator.MediatorReplayOutputEvents
import net.corda.data.messaging.mediator.MediatorState
import net.corda.data.messaging.mediator.SerializationType
import net.corda.messaging.api.mediator.MediatorMessage
import net.corda.messaging.api.mediator.MessagingClient.Companion.MSG_PROP_KEY
import net.corda.messaging.api.mediator.MessagingClient.Companion.MSG_PROP_TOPIC
import net.corda.messaging.api.records.Record
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.nio.ByteBuffer

/**
 * Service used by the Multi-Source Mediator to assist in detecting replays and storing output events.
 */
@Component(service = [MediatorReplayService::class])
class MediatorReplayService @Activate constructor(
    @Reference(service = CordaAvroSerializationFactory::class)
    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory,
) {
    private val serializer = cordaAvroSerializationFactory.createAvroSerializer<Any> { }
    private val bytesDeserializer = cordaAvroSerializationFactory.createAvroDeserializer({}, ByteArray::class.java)
    private val stringDeserializer = cordaAvroSerializationFactory.createAvroDeserializer({}, String::class.java)
    private val avroDeserializer = cordaAvroSerializationFactory.createAvroDeserializer({}, Any::class.java)

    /**
     * Generate the new [MediatorReplayOutputEvents] given the mediators [existingOutputs] when provided with the [newOutputs]
     * @param existingOutputs The existing output events saved to the mediator state
     * @param newOutputs The new outputs to add to the existingOutputs to be stored in the mediator.
     * @return Return a new [MediatorReplayOutputEvents] object containing all the existing outputs with the new outputs added to it.
     */
    fun <K : Any, E : Any> getOutputEvents(
        existingOutputs: List<MediatorReplayOutputEvents>,
        newOutputs: Map<Record<K, E>, MutableList<MediatorMessage<Any>>>
    ): List<MediatorReplayOutputEvents> {
        val mediatorOutputs = existingOutputs.toMutableList()

        newOutputs.forEach { entry ->
            val hash = getInputHash(entry.key)
            val mediatorOutputList = entry.value.map {
                val topic = it.properties.getProperty(MSG_PROP_TOPIC)
                val key = serializeOutput(it.properties.getProperty(MSG_PROP_KEY))
                val payload = serializeOutput(it.payload)
                MediatorReplayOutputEvent(topic, key, payload)
            }
            mediatorOutputs.find { it.inputEventHash == hash }.let {
                if (it != null) {
                    it.outputEvents.addAll(mediatorOutputList)
                } else {
                    mediatorOutputs.add(MediatorReplayOutputEvents(hash, mediatorOutputList))
                }
            }
        }

        return mediatorOutputs
    }

    private fun <T> serializeOutput(any: T?) : MediatorReplayOutputEventType? {
        if (any == null) return null
        val serializationType = when(any) {
            is String -> SerializationType.STRING
            is ByteArray -> SerializationType.BYTEARRAY
            else -> SerializationType.AVRO
        }

        return MediatorReplayOutputEventType(ByteBuffer.wrap(serialize(any)), serializationType)
    }
    /**
     * Compare the [inputRecords] to the existing [mediatorState] to see which records are a replayed records or not.
     * If it is replay then return all the outputs from the [mediatorState] as [MediatorMessage]s to the resulting map.
     * If a given input is not a replayed event the mediator has been before, then no entry in the map is added.
     * @param inputRecords Record to check whether it is a replay or not
     * @param mediatorState The mediator state to check the [inputRecord] against
     * @return Map of replayed input records to their the outputs as [MediatorMessage]s
     */
    fun <K : Any, V : Any> getReplayEvents(
        inputRecords: Set<Record<K, V>>,
        mediatorState: MediatorState
    ): Map<Record<K, V>, List<MediatorMessage<Any>>> {
        val inputHashes = inputRecords.associateBy { getInputHash(it) }
        val replayEvents = mutableMapOf<Record<K, V>, List<MediatorMessage<Any>>>()
        mediatorState.outputEvents.asReversed().forEach { replay ->
            inputHashes[replay.inputEventHash]?.let { inputRecord ->
                replayEvents[inputRecord] = replay.outputEvents.map { it.toMediatorMessage() }
            }
        }

        return replayEvents
    }

    fun MutableMap<String, Any>.getProperty(key: String): String {
        return this[key]?.toString() ?: throw IllegalStateException("Mediator message property $key was null")
    }

    /**
     * Generate a unique hash for the consumer input record to be used as a key when storing outputs.
     * This allows for replay detection of consumer inputs by looking up the hash in the [MediatorReplayOutputEvents]
     * @param inputEvent The consumer input event polled from the bus
     * @return A hash of the input event as bytes
     */
    private fun <K : Any, E : Any> getInputHash(inputEvent: Record<K, E>): ByteBuffer {
        val recordValueBytes = serialize(inputEvent.value)
        check(recordValueBytes != null) {
            "Input record key and value bytes should not be null"
        }
        return ByteBuffer.wrap((recordValueBytes).sha256Bytes())
    }

    private fun serialize(value: Any?) = value?.let { serializer.serialize(it) }

    /**
     * Deserialize to mediator messages. We expect keys to be of type string for now
     * and value objects to be type avro.
     */
    private fun MediatorReplayOutputEvent.toMediatorMessage(): MediatorMessage<Any> {
        val key = deserializeReplayValue(key) ?: throw IllegalStateException("Mediator message key is null after deserialization")
        val payload = deserializeReplayValue(value)
        val properties = mutableMapOf<String, Any>(
            MSG_PROP_TOPIC to topic,
            MSG_PROP_KEY to key
        )
        return MediatorMessage(payload, properties)
    }

    /**
     * Deserialize to mediator messages as their original type
     * The keys and values may have been serialized from strings, bytes arrays or AVRO messages.
     * The result is always a byte array.
     */
    private fun deserializeReplayValue(replayValue: MediatorReplayOutputEventType?) : Any? {
        if (replayValue == null) return null
        val replayValueBytes = replayValue.value.array()
        return when(replayValue.type) {
            SerializationType.BYTEARRAY -> bytesDeserializer.deserialize(replayValueBytes)
            SerializationType.STRING -> stringDeserializer.deserialize(replayValueBytes)
            else -> avroDeserializer.deserialize(replayValueBytes)
        }
    }
}

