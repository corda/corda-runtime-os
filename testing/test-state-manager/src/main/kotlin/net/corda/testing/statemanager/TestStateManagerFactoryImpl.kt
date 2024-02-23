package net.corda.testing.statemanager

import net.corda.libs.configuration.SmartConfig
import net.corda.libs.statemanager.api.CompressionType
import net.corda.libs.statemanager.api.IntervalFilter
import net.corda.libs.statemanager.api.MetadataFilter
import net.corda.libs.statemanager.api.Operation
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.libs.statemanager.api.StateManagerFactory
import net.corda.libs.statemanager.api.StateOperationGroup
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.schema.configuration.StateManagerConfig
import org.osgi.service.component.annotations.Component
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * The real state manager implementation requires postgres to run. As a result, it is impossible to plug it into
 * integration tests and have those execute in a non-postgres environment at present.
 *
 * This can be used as a temporary
 * workaround. However, longer term this may not be feasible.
 */
@Component
class TestStateManagerFactoryImpl : StateManagerFactory {

    private val storage = ConcurrentHashMap<String, State>()

    override fun create(config: SmartConfig, stateType: StateManagerConfig.StateType, compressionType: CompressionType): StateManager {
        return object : StateManager {
            override val name = LifecycleCoordinatorName("TestStateManager", UUID.randomUUID().toString())

            override fun create(states: Collection<State>): Set<String> {
                return states.mapNotNull {
                    storage.putIfAbsent(it.key, it)
                }.map { it.key }.toSet()
            }

            override fun get(keys: Collection<String>): Map<String, State> {
                return keys.mapNotNull { storage[it] }.associateBy { it.key }
            }

            override fun update(states: Collection<State>): Map<String, State> {
                return states.mapNotNull {
                    var output: State? = null
                    storage.compute(it.key) { _, existingState ->
                        if (existingState?.version == it.version) {
                            it.copy(version = it.version + 1)
                        } else {
                            output = it
                            it
                        }
                    }
                    output
                }.associateBy { it.key }
            }

            override fun delete(states: Collection<State>): Map<String, State> {
                return states.mapNotNull {
                    var output: State? = null
                    storage.compute(it.key) { _, existingState ->
                        if (existingState?.version == it.version) {
                            null
                        } else {
                            output = it
                            existingState
                        }
                    }
                    output
                }.associateBy { it.key }
            }

            override fun createOperationGroup(): StateOperationGroup {
                TODO("Not yet implemented")
            }

            override fun updatedBetween(interval: IntervalFilter): Map<String, State> {
                TODO("Not yet implemented")
            }

            override fun findByMetadataMatchingAll(filters: Collection<MetadataFilter>): Map<String, State> {
                TODO("Not yet implemented")
            }

            override fun findByMetadataMatchingAny(filters: Collection<MetadataFilter>): Map<String, State> {
                TODO("Not yet implemented")
            }

            // Only supporting equals for now.
            override fun findUpdatedBetweenWithMetadataFilter(
                intervalFilter: IntervalFilter,
                metadataFilter: MetadataFilter
            ): Map<String, State> {
                return storage.filter { (_, state) ->
                    state.modifiedTime >= intervalFilter.start && state.modifiedTime <= intervalFilter.finish
                }.filter {
                    matchesAll(it.value, listOf(metadataFilter))
                }
            }

            override fun findUpdatedBetweenWithMetadataMatchingAll(
                intervalFilter: IntervalFilter,
                metadataFilters: Collection<MetadataFilter>
            ): Map<String, State> {
                return storage.filter { (_, state) ->
                    state.modifiedTime >= intervalFilter.start && state.modifiedTime <= intervalFilter.finish
                }.filter {
                    matchesAll(it.value, metadataFilters)
                }
            }

            override fun findUpdatedBetweenWithMetadataMatchingAny(
                intervalFilter: IntervalFilter,
                metadataFilters: Collection<MetadataFilter>
            ): Map<String, State> {
                return storage.filter { (_, state) ->
                    state.modifiedTime >= intervalFilter.start && state.modifiedTime <= intervalFilter.finish
                }.filter {
                    matchesAny(it.value, metadataFilters)
                }
            }

            @Suppress("UNCHECKED_CAST")
            private fun matchesAny(state: State, filters: Collection<MetadataFilter>) =
                filters.any {
                    if (state.metadata.containsKey(it.key)) {
                        val source = state.metadata[it.key] as Comparable<Any>

                        when (it.operation) {
                            Operation.Equals -> source == it.value
                            Operation.NotEquals -> source != it.value
                            Operation.LesserThan -> source < it.value
                            Operation.GreaterThan -> source > it.value
                        }
                    } else {
                        false
                    }
                }

            @Suppress("UNCHECKED_CAST")
            private fun matchesAll(state: State, filters: Collection<MetadataFilter>) =
                filters.all {
                    if (state.metadata.containsKey(it.key)) {
                        val source = state.metadata[it.key] as Comparable<Any>

                        when (it.operation) {
                            Operation.Equals -> source == it.value
                            Operation.NotEquals -> source != it.value
                            Operation.LesserThan -> source < it.value
                            Operation.GreaterThan -> source > it.value
                        }
                    } else {
                        false
                    }
                }

            override val isRunning: Boolean
                get() = true

            override fun start() {
            }

            override fun stop() {
            }
        }
    }
}
