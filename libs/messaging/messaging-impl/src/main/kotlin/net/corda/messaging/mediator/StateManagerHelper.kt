package net.corda.messaging.mediator

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.data.messaging.mediator.MediatorState
import net.corda.libs.statemanager.api.Metadata
import net.corda.libs.statemanager.api.STATE_TYPE
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.libs.statemanager.api.metadata
import net.corda.messaging.api.constants.MessagingMetadataKeys.PROCESSING_FAILURE
import net.corda.messaging.api.processor.StateAndEventProcessor
import java.nio.ByteBuffer

/**
 * Helper for working with [StateManager], used by [MultiSourceEventMediatorImpl].
 */
class StateManagerHelper<S : Any>(
    private val serializer: CordaAvroSerializer<Any>,
    private val stateDeserializer: CordaAvroDeserializer<S>,
    private val mediatorStateDeserializer: CordaAvroDeserializer<MediatorState>,
) {

    /**
     * Creates an updated [State] or a new one if there was no previous version.
     *
     * @param key Event's key.
     * @param mediatorState Mediator wrapper state.
     * @param persistedState State being updated.
     * @param newState Updated state.
     */
    fun createOrUpdateState(
        key: String,
        persistedState: State?,
        mediatorState: MediatorState,
        newState: StateAndEventProcessor.State<S>?,
    ) = serialize(newState?.value)?.let { serializedValue ->
        mediatorState.state = ByteBuffer.wrap(serializedValue)
        val mediatorStateBytes = serializer.serialize(mediatorState)
            ?: throw IllegalStateException("Serialized mediator state was null. This should not be possible!")
        val stateType = newState!!.value!!::class.java.name
        State(
            key,
            mediatorStateBytes,
            persistedState?.version ?: State.VERSION_INITIAL_VALUE,
            mergeMetadata(persistedState?.metadata, newState.metadata, stateType),
        )
    }

    /**
     * Marks a state as having failed event mediator processing.
     *
     * In the event processing failed for a non-existent state, a new state is created with the metadata key set. This
     * allows clients to detect issues with any failed processing.
     */
    fun failStateProcessing(key: String, originalState: State?) : State {
        val newMetadata = (originalState?.metadata?.toMutableMap() ?: mutableMapOf()).also {
            it[PROCESSING_FAILURE] = true
        }
        return State(
            key,
            originalState?.value ?: byteArrayOf(),
            version = originalState?.version ?: State.VERSION_INITIAL_VALUE,
            metadata = Metadata(newMetadata)
        )
    }

    private fun mergeMetadata(existing: Metadata?, newMetadata: Metadata?, stateType: String): Metadata {
        val map = (existing ?: metadata()).toMutableMap()
        newMetadata?.forEach { map[it.key] = it.value }
        map[STATE_TYPE] = stateType

        return Metadata(map)
    }

    /**
     * Serializes state value.
     *
     * @param value State value.
     * @return Serialized state value.
     */
    private fun serialize(value: Any?) =
        value?.let { serializer.serialize(it) }

    /**
     * Deserializes state value.
     *
     * @param mediatorState State.
     * @return Deserialized state value.
     */
    fun deserializeValue(mediatorState: MediatorState?) =
        mediatorState?.state?.let { stateDeserializer.deserialize(it.array()) }


    /**
     * Deserializes state value into the MediatorState.
     *
     * @param state State.
     * @return Deserialized MediatorState.
     */
    fun deserializeMediatorState(state: State?) =
        state?.value?.let { mediatorStateDeserializer.deserialize(it) }
}
