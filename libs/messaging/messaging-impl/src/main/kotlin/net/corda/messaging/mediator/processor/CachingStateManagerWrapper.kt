package net.corda.messaging.mediator.processor

import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager

class CachingStateManagerWrapper(val stateManager: StateManager) {
    fun create(states: Collection<State>): Set<String> {
        return stateManager.create(states)
    }

    fun get(keys: Collection<String>): Map<String, State> {
        return stateManager.get(keys)
    }

    fun update(states: Collection<State>): Map<String, State?> {
        return stateManager.update(states)
    }

    fun delete(states: Collection<State>): Map<String, State> {
        return stateManager.delete(states)
    }
}