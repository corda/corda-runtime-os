package net.corda.libs.statemanager.impl.repository

import javax.persistence.EntityManager
import net.corda.libs.statemanager.impl.model.v1.StateEntity
import java.time.Instant

/**
 * Repository for entity operations on state manager entities.
 */
interface StateRepository {

    /**
     * Get entities with the given keys.
     *
     * Transaction should be controlled by the caller.
     *
     * @param entityManager used to interact with the state manager persistence context.
     * @param keys collection of state keys to get entities for.
     * @return list of states found
     */
    fun get(entityManager: EntityManager, keys: Collection<String>): Collection<StateEntity>

    /**
     * Create states into the persistence context.
     *
     * Transaction should be controlled by the caller.
     *
     * @param entityManager used to interact with the state manager persistence context.
     * @param states collection of states to be persisted.
     */
    fun create(entityManager: EntityManager, states: Collection<StateEntity>)

    /**
     * Update states within the persistence context.
     *
     * Transaction should be controlled by the caller.
     *
     * @param entityManager used to interact with the state manager persistence context.
     * @param states collection of states to be updated.
     */
    fun update(entityManager: EntityManager, states: Collection<StateEntity>)

    /**
     * Delete entities with the given keys from the persistence context.
     *
     * Transaction should be controlled by the caller.
     *
     * @param entityManager used to interact with the state manager persistence context.
     * @param keys collection of state keys to delete.
     */
    fun delete(entityManager: EntityManager, keys: Collection<String>)

    /**
     * Retrieve entities that were last updated between [start] and [finish].
     *
     * Transaction should be controlled by the caller.
     *
     * @param entityManager used to interact with the state manager persistence context.
     * @param start lower bound for date filter.
     * @param finish upper bound for date filter.
     */
    fun findUpdatedBetween(entityManager: EntityManager, start: Instant, finish: Instant): Collection<StateEntity>
}
