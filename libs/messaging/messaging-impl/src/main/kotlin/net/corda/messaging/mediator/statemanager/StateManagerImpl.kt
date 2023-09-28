package net.corda.messaging.mediator.statemanager

import net.corda.libs.statemanager.api.IntervalFilter
import net.corda.libs.statemanager.api.Operation
import net.corda.libs.statemanager.api.SingleKeyFilter
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import java.time.Instant

// TODO This is used temporarily until State Manager implementation is finished
@Component(service = [StateManager::class])
class StateManagerImpl  @Activate constructor() : StateManager {
    private val storage = mutableMapOf<String, State>()

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
            val existingState = storage[it.key]
            if (existingState?.version == it.version) {
                storage[it.key] = it
                null
            } else {
                it
            }
        }.associateBy { it.key }
    }

    override fun delete(states: Collection<State>): Map<String, State> {
        TODO("Not yet implemented")
    }

    override fun updatedBetween(intervalFilter: IntervalFilter): Map<String, State> {
        TODO("Not yet implemented")
    }

    override fun find(singleKeyFilter: SingleKeyFilter): Map<String, State> {
        TODO("Not yet implemented")
    }

    override fun findUpdatedBetweenWithMetadataFilter(
        intervalFilter: IntervalFilter,
        singleKeyFilter: SingleKeyFilter
    ): Map<String, State> {
        TODO("Not yet implemented")
    }

    override fun close() {
        TODO("Not yet implemented")
    }
}
