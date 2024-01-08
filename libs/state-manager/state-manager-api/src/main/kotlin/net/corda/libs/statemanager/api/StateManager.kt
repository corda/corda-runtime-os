package net.corda.libs.statemanager.api

import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorName

/**
 * The [StateManager] provides functions to manage states within the underlying persistent storage.
 */
interface StateManager : Lifecycle {

    /**
     * The State Manager lifecycle coordinator identifier.
     *
     * There might be multiple State Manager instances within a single process, the [name] should identify each one
     * uniquely so the [Lifecycle] library can be used to follow regular component events.
     */
    val name: LifecycleCoordinatorName

    /**
     * Persist new [states].
     *
     * A single transactional context is used when interacting with the underlying persistent storage,
     * so all these states will be persisted or none will.
     *
     * Control is only returned to the caller once all [states] that were successfully created have been fully
     * persisted and replicas of the underlying persistent storage, if any, are synced.
     *
     * @param states Collection of states to be persisted.
     * @return Collection of keys for all those states that could not be persisted on the underlying persistent storage.
     */
    fun create(states: Collection<State>): Set<String>

    /**
     * Persist new [states], If states already exist then overwrite them and increment the state version.
     *
     * A single transactional context is used when interacting with the underlying persistent storage,
     * so all these states will be persisted/updated or none will.
     *
     * Control is only returned to the caller once all [states] that were successfully created/updated have been fully
     * persisted and replicas of the underlying persistent storage, if any, are synced.
     *
     * @param states Collection of states to be persisted.
     * @return A map of the previous values associated for the keys that performed an update,
     * or no entries in the map for keys successfully created.
     */
    fun createOrUpdate(states: Collection<State>): Map<String, State>

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
     *      the optimistic locking check. If this state failed to be updated because the key was deleted the key is
     *      associated with null.
     */
    fun update(states: Collection<State>): Map<String, State?>

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
     * Retrieve all states that were updated for the last time between [IntervalFilter.start] (inclusive)
     * and [IntervalFilter.finish] (inclusive). Only states that have been successfully committed and distributed
     * within the underlying persistent storage are returned.
     *
     * @param interval Time filter to use when searching for states.
     * @return States that were last updated between [IntervalFilter.start] and [IntervalFilter.finish] times.
     */
    fun updatedBetween(interval: IntervalFilter): Map<String, State>

    /**
     *  Retrieve all states that have a value associated with [MetadataFilter.key] in their [State.metadata] matching
     *  the provided [MetadataFilter.value] when evaluated through [MetadataFilter.operation]. Only states that have
     *  been successfully committed and distributed within the underlying persistent storage are returned.
     *
     * @param filter Filter parameters to use when searching for states.
     * @return states matching the specified filter.
     */
    fun findByMetadata(filter: MetadataFilter) = findByMetadataMatchingAll(listOf(filter))

    /**
     * Retrieve all states exclusively matching all specified [filters]. Only states that have been successfully
     * committed and distributed within the underlying persistent storage are returned.
     *
     * @param filters Filter parameters to use when searching for states.
     * @return states matching the specified filter.
     */
    fun findByMetadataMatchingAll(filters: Collection<MetadataFilter>): Map<String, State>

    /**
     * Retrieve all states matching any of the [filters]. Only states that have been successfully committed
     * and distributed within the underlying persistent storage are returned.
     *
     * @param filters Filter parameters to use when searching for states.
     * @return states matching the specified filter.
     */
    fun findByMetadataMatchingAny(filters: Collection<MetadataFilter>): Map<String, State>

    /**
     * Retrieve all states, updated for the last time between [IntervalFilter.start] (inclusive) and
     * [IntervalFilter.finish] (inclusive), that have a value associated with [MetadataFilter.key] in their
     * [State.metadata] matching the provided [MetadataFilter.value] when evaluated through [MetadataFilter.operation].
     * Only states that have been successfully committed and distributed within the underlying
     * persistent storage are returned.
     *
     * @param intervalFilter Time filter to use when searching for states.
     * @param metadataFilter Filter parameters to use when searching for states.
     * @return states matching the specified filters.
     */
    fun findUpdatedBetweenWithMetadataFilter(
        intervalFilter: IntervalFilter,
        metadataFilter: MetadataFilter
    ): Map<String, State> = findUpdatedBetweenWithMetadataMatchingAll(intervalFilter, listOf(metadataFilter))

    /**
     * Retrieve all states, updated for the last time between [IntervalFilter.start] (inclusive) and
     * [IntervalFilter.finish] (inclusive), for which all the specified [metadataFilters] exclusively match.
     * Each [MetadataFilter.value] is evaluated through the [MetadataFilter.operation] against the value stored inside
     * the [State.metadata] under the [MetadataFilter.key].
     * Only states that have been successfully committed and distributed within the underlying
     * persistent storage are returned.
     *
     * @param intervalFilter Time filter to use when searching for states.
     * @param metadataFilters Filter parameters to use when searching for states.
     * @return states matching the specified filters.
     */
    fun findUpdatedBetweenWithMetadataMatchingAll(
        intervalFilter: IntervalFilter,
        metadataFilters: Collection<MetadataFilter>
    ): Map<String, State>

    /**
     * Retrieve all states, updated for the last time between [IntervalFilter.start] (inclusive) and
     * [IntervalFilter.finish] (inclusive), for which any of the specified [metadataFilters] match.
     * Each [MetadataFilter.value] is evaluated through the [MetadataFilter.operation] against the value stored inside
     * the [State.metadata] under the [MetadataFilter.key].
     * Only states that have been successfully committed and distributed within the underlying
     * persistent storage are returned.
     *
     * @param intervalFilter Time filter to use when searching for states.
     * @param metadataFilters Filter parameters to use when searching for states.
     * @return states matching the specified filters.
     */
    fun findUpdatedBetweenWithMetadataMatchingAny(
        intervalFilter: IntervalFilter,
        metadataFilters: Collection<MetadataFilter>
    ): Map<String, State>
}
