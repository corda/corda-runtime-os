package net.corda.messaging.mediator

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.libs.statemanager.api.Metadata
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager

/**
 * Helper for working with [StateManager], used by [MultiSourceEventMediatorImpl].
 */
class StateManagerHelper<K : Any, S : Any, E : Any>(
    private val stateManager: StateManager,
    private val stateSerializer: CordaAvroSerializer<S>,
    private val stateDeserializer: CordaAvroDeserializer<S>,
) {

    /**
     * Creates an updated [State] or a new one if there was no previous version.
     *
     * @param key Event's key.
     * @param persistedState State being updated.
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
            persistedState?.version ?: State.VERSION_INITIAL_VALUE,
            persistedState?.metadata ?: Metadata()
        )
    }

    /**
     * Persists states of [ProcessorTask] results.
     *
     * @param processorTaskResults [ProcessorTask] results with updated states.
     * @return The latest states in case persistence failed due to conflict (state being updated by another process in
     * the meantime).
     */
    fun persistStates(processorTaskResults: Collection<ProcessorTask.Result<K, S, E>>): Map<String, State?> {
        val states = processorTaskResults.mapNotNull { result ->
            result.updatedState
        }
        val (newStates, existingStates) = states.partition { state ->
            state.version == State.VERSION_INITIAL_VALUE
        }
        val latestValuesForFailedStates = mutableMapOf<String, State?>()
        if (newStates.isNotEmpty()) {
            val failedStatesKeys = stateManager.create(newStates).keys
            if (failedStatesKeys.isNotEmpty()) {
                val latestStatesValues = stateManager.get(failedStatesKeys)
                latestValuesForFailedStates.putAll(failedStatesKeys.associateWith { key ->
                    latestStatesValues[key]
                })
            }
        }
        if (existingStates.isNotEmpty()) {
            latestValuesForFailedStates.putAll(stateManager.update(existingStates))
        }
        return latestValuesForFailedStates
    }

    /**
     * Serializes state value.
     *
     * @param value State value.
     * @return Serialized state value.
     */
    private fun serialize(value: S?) =
        value?.let { stateSerializer.serialize(it) }

    /**
     * Deserializes state value.
     *
     * @param state State.
     * @return Deserialized state value.
     */
    fun deserializeValue(state: State?) =
        state?.value?.let { stateDeserializer.deserialize(it) }
}