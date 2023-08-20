package net.corda.libs.statemanager

class StateManagerImpl<S> : StateManager<S> {
    override fun get(keys: Set<String>): Map<String, State<S>> {
        TODO("Not yet implemented")
    }

    override fun put(states: Set<State<S>>): Map<String, State<S>> {
        TODO("Not yet implemented")
    }

    override fun delete(keys: Set<String>): Map<String, State<S>> {
        TODO("Not yet implemented")
    }

    override fun filterByModifiedTime(time: Long, comparison: StateManager.ComparisonOperation): Map<String, State<S>> {
        TODO("Not yet implemented")
    }

    override fun filter(key: String, value: Any, comparison: StateManager.ComparisonOperation): Map<String, State<S>> {
        TODO("Not yet implemented")
    }

    override fun filter(name: StateManager.StateFilter, parameters: PrimitiveTypeMap<String, Any>): Map<String, State<S>> {
        TODO("Not yet implemented")
    }

    override fun close() {
        TODO("Not yet implemented")
    }
}