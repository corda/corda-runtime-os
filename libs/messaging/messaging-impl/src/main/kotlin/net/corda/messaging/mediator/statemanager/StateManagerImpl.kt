package net.corda.messaging.mediator.statemanager

import net.corda.messaging.api.mediator.statemanager.Operation
import net.corda.messaging.api.mediator.statemanager.State
import net.corda.messaging.api.mediator.statemanager.StateManager
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import java.time.Instant

@Component(service = [StateManager::class])
class StateManagerImpl  @Activate constructor() : StateManager {
    override fun create(states: Collection<State>): Map<String, Exception> {
        TODO("Not yet implemented")
    }

    override fun get(keys: Collection<String>): Map<String, State> {
        TODO("Not yet implemented")
    }

    override fun update(states: Collection<State>): Map<String, State> {
        TODO("Not yet implemented")
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