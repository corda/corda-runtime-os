package net.corda.messaging

import net.corda.libs.statemanager.api.Metadata
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.metadata
import net.corda.messaging.api.constants.FAILED_STATE
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.mediator.StateManagerHelper

class StateBatch<S: Any>(
    private val stateManagerHelper: StateManagerHelper<S>,
    initialStates: Collection<State>
) {
    private data class StatePair(
        val initialState: State?,
        val finalState: State?
    )

    private val states = initialStates.associate {
        Pair(it.key, StatePair(it, null))
    }.toMutableMap()

    fun updateState(key: String, processingResult: StateAndEventProcessor.State<S>?) {
        val current = states[key]
        val newState = stateManagerHelper.createOrUpdateState(key, current?.initialState, processingResult)
        states[key] = StatePair(current?.initialState, newState)
    }

    fun failState(key: String) {
        val current = states[key]
        val state = current?.finalState ?: current?.initialState ?: State(
            key,
            byteArrayOf()
        )
        val metadata = Metadata(mapOf(FAILED_STATE to true))
        val newState = State(
            state.key,
            state.value,
            state.version,
            mergeMetadata(state.metadata, metadata)
        )
        states[key] = StatePair(current?.initialState, newState)
    }

    private fun mergeMetadata(existing: Metadata?, newMetadata: Metadata?): Metadata {
        val map = (existing ?: metadata()).toMutableMap()
        newMetadata?.forEach {
            map[it.key] = it.value
        }
        return Metadata(map)
    }
}