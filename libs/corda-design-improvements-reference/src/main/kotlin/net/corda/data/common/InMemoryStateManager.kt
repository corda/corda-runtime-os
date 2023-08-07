package net.corda.data.common

import StateManager
import net.corda.data.models.State

class InMemoryStateManager : StateManager {
    override fun <V : Any> get(key: String): State<V> {
        TODO("Not yet implemented")
    }

    override fun <V : Any> store(state: State<V>) {
        TODO("Not yet implemented")
    }

    override fun delete(key: String) {
        TODO("Not yet implemented")
    }
}