package net.corda.flow.rest.impl

import net.corda.libs.statemanager.api.IntervalFilter
import net.corda.libs.statemanager.api.MetadataFilter
import net.corda.libs.statemanager.api.Operation
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.libs.statemanager.api.StateOperationGroup
import net.corda.lifecycle.LifecycleCoordinatorName
import java.time.Instant

fun getMockStateManager(): StateManager {
    return object : StateManager {
        private val stateStore = mutableMapOf<String, State>()
        override val name = LifecycleCoordinatorName("MockStateManager")

        override fun create(states: Collection<State>): Set<String> {
            val failedKeys = mutableSetOf<String>()

            states.forEach { state ->
                if (state.key in stateStore) {
                    failedKeys.add(state.key)
                } else {
                    stateStore[state.key] = state.copy(modifiedTime = Instant.now())
                }
            }

            return failedKeys
        }

        override fun get(keys: Collection<String>): Map<String, State> {
            return keys.mapNotNull { key -> stateStore[key]?.let { key to it } }.toMap()
        }

        override fun update(states: Collection<State>): Map<String, State?> {
            val failedUpdates = mutableMapOf<String, State?>()

            states.forEach { state ->
                val currentState = stateStore[state.key]
                if (currentState == null || currentState.version != state.version) {
                    // State does not exist or version mismatch
                    failedUpdates[state.key] = currentState
                } else {
                    // Optimistic locking condition met
                    val updatedState = state.copy(version = currentState.version + 1, modifiedTime = Instant.now())
                    stateStore[state.key] = updatedState
                }
            }

            return failedUpdates
        }

        override fun delete(states: Collection<State>): Map<String, State> {
            val failedDeletion = mutableMapOf<String, State>()

            states.forEach { state ->
                val currentState = stateStore[state.key]
                if (currentState != null && currentState.version == state.version) {
                    stateStore.remove(state.key)
                } else {
                    currentState?.let { failedDeletion[state.key] = currentState }
                }
            }

            return failedDeletion
        }

        override fun updatedBetween(interval: IntervalFilter): Map<String, State> {
            TODO("Not yet implemented")
        }

        @Suppress("UNCHECKED_CAST")
        override fun findByMetadataMatchingAll(filters: Collection<MetadataFilter>): Map<String, State> {
            var filteredStates: Map<String, State> = stateStore
            for ((key, operation, value) in filters) {
                filteredStates = when (operation) {
                    Operation.Equals ->
                        filteredStates.filter { it.value.metadata[key] == value }

                    Operation.NotEquals ->
                        filteredStates.filter { it.value.metadata[key] != value }

                    Operation.LesserThan ->
                        filteredStates.filter { (it.value.metadata[key] as Comparable<Any>) < value }

                    Operation.GreaterThan ->
                        filteredStates.filter { (it.value.metadata[key] as Comparable<Any>) > value }
                }
            }
            return filteredStates
        }

        override fun findByMetadataMatchingAny(filters: Collection<MetadataFilter>): Map<String, State> {
            TODO("Not yet implemented")
        }

        override fun findUpdatedBetweenWithMetadataMatchingAll(
            intervalFilter: IntervalFilter,
            metadataFilters: Collection<MetadataFilter>
        ): Map<String, State> {
            TODO("Not yet implemented")
        }

        override fun findUpdatedBetweenWithMetadataMatchingAny(
            intervalFilter: IntervalFilter,
            metadataFilters: Collection<MetadataFilter>
        ): Map<String, State> {
            TODO("Not yet implemented")
        }

        override fun createOperationGroup(): StateOperationGroup {
            TODO("Not yet implemented")
        }

        override val isRunning = true

        override fun start() { }

        override fun stop() { }
    }
}
