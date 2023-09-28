package net.corda.libs.statemanager.api

import java.time.Instant

/**
 * The [StateManager] provides functions to manage states within the underlying persistent storage.
 */
interface StateManager : AutoCloseable {

    /**
     * Persist new [states].
     *
     * Every operation uses its own transactional context when interacting with the underlying persistent storage and,
     * as a result, some states might have been successfully persisted and some might have not.
     * It's the responsibility of calling API to decide whether the operation can be retried or not, based on the
     * [Exception] returned for the relevant key.
     *
     * Control is only returned to the caller once all [states] that were successfully created have been fully
     * persisted and replicas of the underlying persistent storage, if any, are synced.
     *
     * @param states Collection of states to be persisted.
     * @return Collection of keys for all those states that could not be persisted on the underlying persistent storage,
     *          along with the actual reason for the failures.
     */
    fun create(states: Collection<State>): Map<String, Exception>

    /**
     * Get all states referenced by [keys].
     * Only states that have been successfully persisted and distributed within the underlying persistent storage
     * are returned.
     *
     * @param keys Collection of keys to use when querying the underlying persistent storage for states.
     * @return Map of states, associated by key for easier access, found in the underlying persistent storage.
     */
    fun get(keys: Collection<String>): Map<String, State>

    /**
     * Update existing [states].
     *
     * Optimistic locking is used to verify whether the persistent state has been already modified by another thread
     * or process, in which case the state will be considered not updatable and the most recent version of it will be
     * returned to the calling API. It's the responsibility of calling API to decide whether the operation should
     * be retried.
     *
     * For all other states (that is, those that are updatable), a single transactional context is used when
     * interacting with the underlying persistent storage, so all these states will be persisted or none will. Control
     * is only returned to the caller once all updatable [states] have been updated and replicas of the underlying
     * persistent storage, if any, are synced.
     *
     * @param states Collection of states to be updated.
     * @return Map with the most up-to-date version of the states, associated by key for easier access, that failed
     *      the optimistic locking check.
     */
    fun update(states: Collection<State>): Map<String, State>

    /**
     * Delete existing [states].
     *
     * Optimistic locking is used to verify whether the persistent state has been already modified by another thread
     * or process, in which case the state will be considered not deletable and the most recent version of it will be
     * returned to the calling API. It's the responsibility of calling API to decide whether the operation should
     * be retried.
     *
     * For all other states (that is, those that are deletable), a single transactional context is used when
     * interacting with the underlying persistent storage, so all these states will be deleted or none will. Control
     * is only returned to the caller once all deletable [states] have been deleted and replicas of the underlying
     * persistent storage, if any, are synced.
     *
     * @param states Collection of states to be updated.
     * @return Map with the most up-to-date version of the states, associated by key for easier access, that failed
     *      the optimistic locking check.
     */
    fun delete(states: Collection<State>): Map<String, State>

    /**
     * Retrieve all states that were updated for the last time between [start] (inclusive) and [finish] (inclusive).
     *
     * @param start Time filter lower bound (inclusive).
     * @param finish Time filter upper bound (inclusive).
     * @return States that were last updated between [start] and [finish] times.
     */
    fun updatedBetween(start: Instant, finish: Instant): Map<String, State>

    /**
     * Retrieve all states for which the value corresponding to the [key] within the [State.metadata] matches the
     * custom [operation]. Only states that have been successfully committed and distributed within the underlying
     * persistent storage are returned.
     *
     * @param key The name of the key in the [State.metadata] to apply the comparison on.
     * @param operation The comparison operation to perform (">", "=", "<", "<>", etc.).
     * @param value The value to compare against.
     * @return states for which the [State.metadata] has [key] for which [value] matches [operation].
     */
    fun find(key: String, operation: Operation, value: Any): Map<String, State>

    /**
     * Retrieve all states, updated for the last time between [start] (inclusive) and [finish] (inclusive), for which
     * the value corresponding to the [key] within the [State.metadata] matches the custom [operation]. Only states
     * that have been successfully committed and distributed within the underlying persistent storage are returned.
     *
     * @param start Time filter lower bound (inclusive).
     * @param finish Time filter upper bound (inclusive).
     * @param key The name of the key in the [State.metadata] to apply the comparison on.
     * @param operation The comparison operation to perform (">", "=", "<", "<>", etc.).
     * @param value The value to compare against.
     * @return states for which the [State.metadata] has [key] for which [value] matches [operation].
     */
    fun findUpdatedBetweenWithMetadataFilter(
        start: Instant, finish: Instant,
        key: String, operation: Operation, value: Any
    ): Map<String, State>
}
