package net.corda.libs.statemanager

interface StateManager<S> : AutoCloseable {

    /**
     * Complex metadata filters provided out of the box by the State Manager.
     * These should only be used for cases on which basic key filtering is not sufficient. At the moment of writing,
     * there are no known use cases that fall under this category, so below instances are just examples.
     */
    enum class StateFilter {
        SequenceNumberBetween
    }

    /**
     * Supported comparison operations on metadata keys.
     */
    enum class ComparisonOperation {
        Equals,
        NotEquals,
        LesserThan,
        GreaterThan,
    }

    /**
     * Get all states referenced by [keys].
     * Only states that have been successfully committed and distributed within the underlying persistent
     * storage are returned.
     */
    fun get(keys: Set<String>): Map<String, State<S>>

    /**
     * Update [states] into the underlying storage.
     * Control is only returned to the caller once all [states] have been updated and replicas of the underlying
     * persistent storage, if any, are synced.
     * Optimistic locking is used when trying to update the [states].
     *
     * @return states that could not be updated due to mismatch versions.
     */
    fun put(states: Set<State<S>>): Map<String, State<S>>

    /**
     * Delete all states referenced by [keys] from the underlying storage.
     * Control is only returned to the caller once all states have been deleted and replicas of the underlying
     * persistent storage, if any, are synced.
     *
     * @return states that could not be deleted due to mismatch versions.
     */
    fun delete(keys: Set<String>): Map<String, State<S>>

    /**
     * Locate states where the [State.modifiedTime] matches [comparison] when executed against [time].
     *
     * @return states for which the [State.modifiedTime] matches [comparison] when executed against [time].
     */
    fun filterByModifiedTime(time: Long, comparison: ComparisonOperation): Map<String, State<S>>

    /**
     * Locate states where the [value] associated with the [key] in [State.metadata] matches the specified [comparison].
     *
     * @return states for which the [State.metadata] has [key] for which [value] matches [comparison].
     */
    fun filter(key: String, value: Any, comparison: ComparisonOperation): Map<String, State<S>>

    /**
     * Executes the [name] filter using the specified [parameters] against the underlying persistent storage. The filter
     * is only applied to the [State.metadata].
     *
     * @return states for which the [State.metadata] was matched by the filter [name].
     */
    fun filter(name: StateFilter, parameters: PrimitiveTypeMap<String, Any>): Map<String, State<S>>
}