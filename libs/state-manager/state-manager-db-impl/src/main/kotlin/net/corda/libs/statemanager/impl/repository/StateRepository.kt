package net.corda.libs.statemanager.impl.repository

import net.corda.libs.statemanager.api.IntervalFilter
import net.corda.libs.statemanager.api.MetadataFilter
import net.corda.libs.statemanager.impl.model.v1.StateEntity
import javax.persistence.EntityManager

/**
 * Repository for entity operations on state manager entities.
 */
interface StateRepository {

    /**
     * Create state into the persistence context.
     * Transaction should be controlled by the caller.
     *
     * @param entityManager Used to interact with the state manager persistence context.
     * @param state State entity to persist.
     */
    fun create(entityManager: EntityManager, state: StateEntity)

    /**
     * Get states with the given keys.
     * Transaction should be controlled by the caller.
     *
     * @param entityManager Used to interact with the state manager persistence context.
     * @param keys Collection of state keys to get entities for.
     * @return Collection of states found.
     */
    fun get(entityManager: EntityManager, keys: Collection<String>): Collection<StateEntity>

    /**
     * Update states within the persistence context.
     * Transaction should be controlled by the caller.
     *
     * @param entityManager Used to interact with the state manager persistence context.
     * @param states Collection of states to be updated.
     * @return Collection of keys for states that could not be updated due to optimistic locking check failure.
     */
    fun update(entityManager: EntityManager, states: Collection<StateEntity>): Collection<String>

    /**
     * Delete states with the given keys from the persistence context.
     * Transaction should be controlled by the caller.
     *
     * @param entityManager Used to interact with the state manager persistence context.
     * @param states Collection of states to be deleted.
     * @return Collection of keys for states that could not be deleted due to optimistic locking check failure.
     */
    fun delete(entityManager: EntityManager, states: Collection<StateEntity>): Collection<String>

    /**
     * Retrieve entities that were lastly updated between [IntervalFilter.start] and [IntervalFilter.finish].
     * Transaction should be controlled by the caller.
     *
     * @param entityManager Used to interact with the state manager persistence context.
     * @param interval Lower and upper bounds to use when filtering by last modified time.
     * @return Collection of states found.
     */
    fun updatedBetween(entityManager: EntityManager, interval: IntervalFilter): Collection<StateEntity>

    /**
     * Filter states based on a list of custom single key filters over the [StateEntity.metadata], only states matching
     * all [filters] are returned.
     * Transaction should be controlled by the caller.
     *
     * @param entityManager Used to interact with the state manager persistence context.
     * @param filters List of filter to use when searching for entities.
     * @return Collection of states found.
     */
    fun filterByAll(entityManager: EntityManager, filters: Collection<MetadataFilter>): Collection<StateEntity>

    /**
     * Filter states based on a list of custom single key filters over the [StateEntity.metadata], states matching
     * any of the [filters] are returned.
     * Transaction should be controlled by the caller.
     *
     * @param entityManager Used to interact with the state manager persistence context.
     * @param filters List of filter to use when searching for entities.
     * @return Collection of states found.
     */
    fun filterByAny(entityManager: EntityManager, filters: Collection<MetadataFilter>): Collection<StateEntity>

    /**
     * Filter states based on a custom comparison operation to be executed against a single key within the metadata and
     * the last updated time.
     * Transaction should be controlled by the caller.
     *
     * @param entityManager used to interact with the state manager persistence context.
     * @param interval Lower and upper bound to use when filtering by time.
     * @param filter Filter to use when searching for entities.
     * @return Collection of states found.
     */
    @Suppress("LongParameterList")
    fun filterByUpdatedBetweenAndMetadata(
        entityManager: EntityManager,
        interval: IntervalFilter,
        filter: MetadataFilter
    ): Collection<StateEntity>
}
