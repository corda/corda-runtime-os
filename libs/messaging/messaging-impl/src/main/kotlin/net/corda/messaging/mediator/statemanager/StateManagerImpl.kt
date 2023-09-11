package net.corda.messaging.mediator.statemanager

import net.corda.messaging.api.mediator.statemanager.State
import net.corda.messaging.api.mediator.statemanager.StateManager
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import java.time.Instant

@Component(service = [StateManager::class])
class StateManagerImpl  @Activate constructor() : StateManager {
    override fun <S : Any> create(clazz: Class<S>, states: Set<State<S>>) {
        TODO("Not yet implemented")
    }

    override fun <S : Any> get(clazz: Class<S>, keys: Set<String>): Map<String, State<S>> {
        TODO("Not yet implemented")
    }

    override fun <S : Any> update(clazz: Class<S>, states: Set<State<S>>): Map<String, State<S>> {
        TODO("Not yet implemented")
    }

    override fun <S : Any> delete(clazz: Class<S>, keys: Set<String>): Map<String, State<S>> {
        TODO("Not yet implemented")
    }

    override fun <S : Any> getUpdatedBetween(clazz: Class<S>, start: Instant, finish: Instant): Map<String, State<S>> {
        TODO("Not yet implemented")
    }

    override fun close() {
        TODO("Not yet implemented")
    }
}