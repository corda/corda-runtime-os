package net.corda.session.mapper.service.integration

import net.corda.libs.configuration.SmartConfig
import net.corda.libs.statemanager.api.IntervalFilter
import net.corda.libs.statemanager.api.MetadataFilter
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.libs.statemanager.api.StateManagerFactory
import org.osgi.service.component.annotations.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * The real state manager implementation requires postgres to run. As a result, it is impossible to plug it into the
 * flow mapper integration tests and have those execute in a non-postgres environment at present.
 *
 * The flow mapper integration tests do not currently need the state manager and so this can be used as a temporary
 * workaround. However, longer term this will not be feasible.
 */
@Component
class TestStateManagerFactoryImpl : StateManagerFactory {
    companion object {
        private val storage = ConcurrentHashMap<String, State>()

        fun clear() = storage.clear()
    }

    override fun create(config: SmartConfig): StateManager {
        return object : StateManager {
            override fun close() {
            }

            override fun create(states: Collection<State>): Map<String, Exception> {
                return states.mapNotNull {
                    storage.putIfAbsent(it.key, it)
                }.associate { it.key to RuntimeException("State already exists [$it]") }
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
                }.filter { (_, state) ->
                    state.metadata.containsKey(metadataFilter.key) && state.metadata[metadataFilter.key] == metadataFilter.value
                }
            }
        }
    }
}