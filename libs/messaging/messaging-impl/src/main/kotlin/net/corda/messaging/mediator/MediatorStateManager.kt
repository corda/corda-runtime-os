package net.corda.messaging.mediator

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.messaging.api.mediator.statemanager.Metadata
import net.corda.messaging.api.mediator.statemanager.State
import net.corda.messaging.api.mediator.statemanager.StateManager

/**
 * Helper for working with [StateManager], used by [MultiSourceEventMediatorImpl].
 */
class MediatorStateManager<K : Any, S : Any, E : Any>(
    private val stateManager: StateManager,
    private val serializer: CordaAvroSerializer<Any>,
    private val stateDeserializer: CordaAvroDeserializer<S>,
) {

    /**
     * Creates an updated [State] or a new one if there was no previous version.
     *
     * @param key Event's key.
     * @param persistedState State bieing updated.
     * @param newValue Updated state value.
     */
    fun createOrUpdateState(
        key: String,
        persistedState: State?,
        newValue: S?,
    ) = serialize(newValue)?.let { serializedValue ->
        State(
            key,
            serializedValue,
            persistedState?.version ?: State.INITIAL_VERSION,
            persistedState?.metadata ?: Metadata()
        )
    }

    /**
     * Persists states of [ProcessorTask] results.
     *
     * @param processorTaskResults [ProcessorTask] results with updated states.
     * @return The latest states in case persistence failed due to conflict (state being updated by another process in
     * the meanwhile).
     */
    fun persistStates(processorTaskResults: Collection<ProcessorTask.Result<K, S, E>>): Map<String, State> {
        val states = processorTaskResults.mapNotNull { result ->
            result.updatedState
        }
        val (newStates, existingStates) = states.partition { it.version == State.INITIAL_VERSION }
        val invalidStates = mutableMapOf<String, State>()
        if (newStates.isNotEmpty()) {
            invalidStates.putAll(stateManager.update(newStates))
        }
        if (existingStates.isNotEmpty()) {
            invalidStates.putAll(stateManager.update(existingStates))
        }
        return invalidStates
    }

    /**
     * Serializes state value.
     *
     * @param value State value.
     * @return Serialized state value.
     */
    private fun serialize(value: S?) =
        value?.let { serializer.serialize(it) }

    /**
     * Deserializes state value.
     *
     * @param state State.
     * @return Deserialized state value.
     */
    fun deserializeValue(state: State?) =
        state?.value?.let { stateDeserializer.deserialize(it) }
}