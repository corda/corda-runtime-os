package net.corda.messaging.mediator.processor

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.crypto.cipher.suite.sha256Bytes
import net.corda.data.messaging.mediator.MediatorReplayOutputEvent
import net.corda.data.messaging.mediator.MediatorReplayOutputEvents
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

    /**
     * Generate the new [MediatorReplayOutputEvents] given the mediators [existingOutputs] when provided with the [newOutputs]
     * @param existingOutputs The existing output events saved to the mediator state/
     * @param newOutputs The new outputs to add to the existingOutputs to be stored in the mediator.
     * @return Return a new [MediatorReplayOutputEvents] object containing all the existing outputs with the new outputs added to it.
     */
    fun <K : Any, E : Any> getOutputEvents(
        existingOutputs: MutableList<MediatorReplayOutputEvents>,
        newOutputs: MutableMap<Record<K, E>, MutableList<MediatorMessage<Any>>>
    ): MutableList<MediatorReplayOutputEvents> {
        val mediatorOutputs = existingOutputs.toMutableList()

        newOutputs.onEach { entry ->
            val hash = getInputHash(entry.key)
            val mediatorOutputList = entry.value.map {
                val topic = it.properties.getProperty(MSG_PROP_TOPIC)
                val key = ByteBuffer.wrap(serializer.serialize(it.properties.getProperty(MSG_PROP_KEY)))
                val payload = ByteBuffer.wrap(serialize(it.payload))
                MediatorReplayOutputEvent(topic, key, payload)
            }
            mediatorOutputs.add(MediatorReplayOutputEvents(hash, mediatorOutputList))
        }

        return mediatorOutputs
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
        val recordKeyBytes = serialize(inputEvent.key)
        val recordValueBytes = serialize(inputEvent.value)
        if (recordKeyBytes == null || recordValueBytes == null)
            throw IllegalStateException("Input record key and value bytes should not be null")
        return ByteBuffer.wrap(recordKeyBytes + recordValueBytes.sha256Bytes())
    }

    fun serialize(value: Any?) = value?.let { serializer.serialize(it) }
}