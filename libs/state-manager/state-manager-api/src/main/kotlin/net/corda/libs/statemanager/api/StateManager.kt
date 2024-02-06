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
     * Every operation uses its own transactional context when interacting with the underlying persistent storage and,
     * as a result, some states might have been successfully persisted and some might have not.
     * It's the responsibility of calling API to decide whether the operation can be retried or not, based on the
     * [Exception] returned for the relevant key.
     *
     * Control is only returned to the caller once all [states] that were successfully created have been fully
     * persisted and replicas of the underlying persistent storage, if any, are synced.
     *
     * If the [states] contains more than one state with the same key, an exception will be thrown.
     *
     * @param states Collection of states to be persisted.
     * @return Collection of keys for all those states that could not be persisted on the underlying persistent storage.
     */
    fun create(states: Collection<State>): Set<String>

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
     * Typical usage is to get some states, e.g. using `findByMetadataMatchingAll`, then make changes to the
     * state content while leaving the version number alone, then try calling `update`.  If the result is non-empty,
     * see if the update you wanted has already been made, and if not sleep for a random time, try the update again.
     *
     * If we have `X` and `Y` trying to update the same state and `Y` wins the race, and:
     *
     * - `X` and `Y` are doing different things, then `X` needs to remake its change and try again.
     * - `X` and `Y` are doing the same thing, then `X` can check and move on. You may not want to sleep
     *    before rechecking the first time around if this is the common case.
     *
     * @param states Collection of states to be updated. Each state record has a version field; it should be the
     *      version currently in the database. The State Manager will increment the version it stores by one
     *      if the update succeeds; calling code must not change the version.
     *
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

    /**
     * Create a new operation group.
     *
     * An operation group can be used to logically group together a set of create, update and delete operations. These
     * operations will use the same underlying context for communicating with the implementation backend. For example,
     * with the database backend, all these operations will be completed as part of the same database transaction.
     *
     * @return The group builder to which operations can be added.
     */
    fun createOperationGroup(): StateOperationGroup
}
