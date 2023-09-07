package net.corda.libs.statemanager.api

import java.time.Instant

/**
 * The [StateManager] provides functions to manage states within the underlying persistent storage.
 */
interface StateManager : AutoCloseable {

    /**
     * Create [states] into the underlying storage.
     * Control is only returned to the caller once all [states] have been updated and replicas of the underlying
     * persistent storage, if any, are synced.
     *
     * @param states collection of states to be persisted.
     * @return states that could not be created on the persistent storage, along with the actual reason for the failures.
     */
    fun create(states: Collection<State>): Map<String, Exception>

    /**
     * Get all states referenced by [keys].
     * Only states that have been successfully committed and distributed within the underlying persistent
     * storage are returned.
     *
     * @param keys collection of state keys to use when querying the persistent storage.
     * @return states found in the underlying persistent storage.
     */
    fun get(keys: Collection<String>): Map<String, State>

    /**
     * Update [states] within the underlying storage.
     * Control is only returned to the caller once all [states] have been updated and replicas of the underlying
     * persistent storage, if any, are synced.
     *
     * @return states that could not be updated due to mismatch versions.
     */
    fun update(states: Collection<State>): Map<String, State>

    /**
     * Delete all states referenced by [keys] from the underlying storage.
     * Control is only returned to the caller once all states have been deleted and replicas of the underlying
     * persistent storage, if any, are synced.
     *
     * @return states that could not be deleted due to mismatch versions.
     */
    fun delete(keys: Collection<String>): Map<String, State>

    /**
     * Retrieve all states that were last updated between [start] and [finish] times.
     *
     * @return states that were last updated between [start] and [finish] times.
     */
    fun getUpdatedBetween(start: Instant, finish: Instant): Map<String, State>
}
