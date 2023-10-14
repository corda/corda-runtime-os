package net.corda.messaging.mediator.statemanager

import net.corda.libs.statemanager.api.IntervalFilter
import net.corda.libs.statemanager.api.MetadataFilter
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import java.util.concurrent.ConcurrentHashMap

// TODO This is used temporarily until State Manager implementation is finished
@Component(service = [StateManager::class])
class StateManagerImpl @Activate constructor() : StateManager {
    private val storage = ConcurrentHashMap<String, State>()

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

    override fun findUpdatedBetweenWithMetadataFilter(
        intervalFilter: IntervalFilter,
        metadataFilter: MetadataFilter
    ): Map<String, State> {
        TODO("Not yet implemented")
    }

    override fun close() {
        TODO("Not yet implemented")
    }
}
