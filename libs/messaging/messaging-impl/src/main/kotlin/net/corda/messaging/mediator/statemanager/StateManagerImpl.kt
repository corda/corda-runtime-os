package net.corda.messaging.mediator.statemanager

import net.corda.libs.statemanager.api.Operation
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

    override fun getUpdatedBetween(start: Instant, finish: Instant): Map<String, State> {
        TODO("Not yet implemented")
    }

    override fun find(key: String, operation: Operation, value: Any): Map<String, State> {
        TODO("Not yet implemented")
    }

    override fun close() {
        TODO("Not yet implemented")
    }

}