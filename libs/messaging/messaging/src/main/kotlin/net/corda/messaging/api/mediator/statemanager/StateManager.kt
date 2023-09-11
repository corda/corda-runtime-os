package net.corda.messaging.api.mediator.statemanager

import java.time.Instant

interface StateManager : AutoCloseable {
    /**
     * Create [states] into the underlying storage.
     * Control is only returned to the caller once all [states] have been updated and replicas of the underlying
     * persistent storage, if any, are synced.
     */
    fun <S : Any> create(clazz: Class<S>, states: Set<State<S>>)

    /**
     * Get all states referenced by [keys].
     * Only states that have been successfully committed and distributed within the underlying persistent
     * storage are returned.
     */
    fun <S : Any> get(clazz: Class<S>, keys: Set<String>): Map<String, State<S>>

    /**
     * Update [states] within the underlying storage.
     * Control is only returned to the caller once all [states] have been updated and replicas of the underlying
     * persistent storage, if any, are synced.
     *
     * @return states that could not be updated due to mismatch versions.
     */
    fun <S : Any> update(clazz: Class<S>, states: Set<State<S>>): Map<String, State<S>>

    /**
     * Delete all states referenced by [keys] from the underlying storage.
     * Control is only returned to the caller once all states have been deleted and replicas of the underlying
     * persistent storage, if any, are synced.
     *
     * @return states that could not be deleted due to mismatch versions.
     */
    fun <S : Any> delete(clazz: Class<S>, keys: Set<String>): Map<String, State<S>>

    /**
     * Retrieve all states that were last updated between [start] and [finish] times.
     *
     * @return states that were last updated between [start] and [finish] times.
     */
    fun <S : Any> getUpdatedBetween(clazz: Class<S>, start: Instant, finish: Instant): Map<String, State<S>>
}