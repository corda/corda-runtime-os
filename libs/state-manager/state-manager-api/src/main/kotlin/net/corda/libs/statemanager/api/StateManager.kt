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
     * @param states Collection of states to be persisted.
     * @return States that could not be created on the persistent storage, along with the actual reason for the failures.
     */
    fun create(states: Collection<State>): Map<String, Exception>

    /**
     * Get all states referenced by [keys].
     * Only states that have been successfully committed and distributed within the underlying persistent
     * storage are returned.
     *
     * @param keys Collection of state keys to use when querying the persistent storage.
     * @return States found in the underlying persistent storage.
     */
    fun get(keys: Collection<String>): Map<String, State>

    /**
     * Update [states] within the underlying storage.
     * Control is only returned to the caller once all [states] have been updated and replicas of the underlying
     * persistent storage, if any, are synced.
     * The operation is transactional, either all [states] are updated or none is.
     *
     * @param states Collection of states to be updated.
     * @return States that could not be updated due to mismatch versions.
     */
    fun update(states: Collection<State>): Map<String, State>

    /**
     * Delete all [states] from the underlying storage.
     * Control is only returned to the caller once all states have been deleted and replicas of the underlying
     * persistent storage, if any, are synced.
     * The operation is transactional, either all [states] are deleted or none is.
     *
     * @param states Collection of states to be deleted.
     * @return States that could not be deleted due to mismatch versions.
     */
    fun delete(states: Collection<State>): Map<String, State>

    /**
     * Retrieve all states that were updated for the last time between [start] (inclusive) and [finish] (inclusive).
     *
     * @param start Time filter lower bound (inclusive).
     * @param finish Time filter upper bound (inclusive).
     * @return States that were last updated between [start] and [finish] times.
     */
    fun getUpdatedBetween(start: Instant, finish: Instant): Map<String, State>

    /**
     * Retrieve states based on custom [operation] to be executed against a single [key] within the [State.metadata].
     * Only states that have been successfully committed and distributed within the underlying persistent
     * storage are returned.
     *
     * @param key The name of the key in the [State.metadata] to apply the comparison on.
     * @param operation The comparison operation to perform (">", "=", "<", "<>", etc.).
     * @param value The value to compare against.
     * @return states for which the [State.metadata] has [key] for which [value] matches [operation].
     */
    fun find(key: String, operation: Operation, value: Any): Map<String, State>
}
