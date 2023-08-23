package net.corda.libs.statemanager

/**
 * The [StateManager] provides functions to manage states to the underlying storage mechanism.
 */
interface StateManager : AutoCloseable {
    /**
     * Get all states referenced by [keys].
     * Only states that have been successfully committed and distributed within the underlying persistent
     * storage are returned.
     */
    fun <S : Any> get(clazz: Class<S>, keys: Set<String>): Map<String, State<S>>

    /**
     * Update [states] into the underlying storage.
     * Control is only returned to the caller once all [states] have been updated and replicas of the underlying
     * persistent storage, if any, are synced.
     * Optimistic locking is used when trying to update the [states].
     *
     * @return states that could not be updated due to mismatch versions.
     */
    fun <S : Any> put(clazz: Class<S>, states: Set<State<S>>): Map<String, State<S>>
}