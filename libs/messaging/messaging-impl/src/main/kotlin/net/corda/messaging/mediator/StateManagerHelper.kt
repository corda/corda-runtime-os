package net.corda.messaging.mediator

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.data.messaging.mediator.MediatorState
import net.corda.libs.statemanager.api.Metadata
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.libs.statemanager.api.metadata
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
            ?: throw IllegalStateException("Serialized mediator state was null. This should not be impossible!")
        State(
            key,
            mediatorStateBytes,
            persistedState?.version ?: State.VERSION_INITIAL_VALUE,
            mergeMetadata(persistedState?.metadata, newState?.metadata),
        )
    }

    private fun mergeMetadata(existing: Metadata?, newMetadata: Metadata?): Metadata {
        val map = (existing ?: metadata()).toMutableMap()
        newMetadata?.forEach {
            map[it.key] = it.value
        }
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
     * @param state State.
     * @return Deserialized state value.
     */
    fun deserializeValue(mediatorState: MediatorState?) =
        mediatorState?.state?.let { stateDeserializer.deserialize(it.array()) }


    /**
     * Deserializes state value into the MediatorState.
     *
     * @param state State.
     * @return Deserialized MediatorState value.
     */
    fun deserializeMediatorState(state: State?) =
        state?.value?.let { mediatorStateDeserializer.deserialize(it) }
}