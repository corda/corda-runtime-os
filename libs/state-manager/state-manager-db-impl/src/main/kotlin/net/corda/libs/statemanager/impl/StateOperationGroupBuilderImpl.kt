package net.corda.libs.statemanager.impl

import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateOperationGroupBuilder

class StateOperationGroupBuilderImpl : StateOperationGroupBuilder {

    private val stateKeys = mutableSetOf<String>()
    private val creates = mutableListOf<State>()
    private val updates = mutableListOf<State>()
    private val deletes = mutableListOf<State>()

    override fun create(states: Collection<State>): StateOperationGroupBuilder {
        addToList(states, creates)
        return this
    }

    override fun update(states: Collection<State>): StateOperationGroupBuilder {
        addToList(states, updates)
        return this
    }

    override fun delete(states: Collection<State>): StateOperationGroupBuilder {
        addToList(states, deletes)
        return this
    }

    private fun addToList(states: Collection<State>, list: MutableList<State>) {
        states.forEach { state ->
            if (state.key in stateKeys) {
                throw IllegalArgumentException(
                    "Attempted to add state with key ${state.key} more than once to the same update batch"
                )
            }
            stateKeys.add(state.key)
            list.add(state)
        }
    }

    override fun execute(): Map<String, State?> {
        TODO("Not yet implemented")
    }
}