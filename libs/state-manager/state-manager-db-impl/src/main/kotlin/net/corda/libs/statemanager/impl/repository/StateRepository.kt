package net.corda.libs.statemanager.impl.repository

import net.corda.libs.statemanager.api.Operation
import javax.persistence.EntityManager
import net.corda.libs.statemanager.impl.model.v1.StateEntity
import java.time.Instant

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
     */
    fun update(entityManager: EntityManager, states: Collection<StateEntity>)

    /**
     * Delete states with the given keys from the persistence context.
     * Transaction should be controlled by the caller.
     *
     * @param entityManager Used to interact with the state manager persistence context.
     * @param keys Collection of states to delete.
     */
    fun delete(entityManager: EntityManager, keys: Collection<String>)

    /**
     * Retrieve entities that were last updated between [start] and [finish].
     * Transaction should be controlled by the caller.
     *
     * @param entityManager used to interact with the state manager persistence context.
     * @param start Lower bound for the time  filter.
     * @param finish Upper bound for the time filter.
     */
    fun findUpdatedBetween(entityManager: EntityManager, start: Instant, finish: Instant): Collection<StateEntity>

    /**
     * Filter states based on a custom comparison operation to be executed against a single key within the metadata.
     *
     * @param key The name of the key in the metadata to apply the comparison on.
     * @param operation The comparison operation to perform.
     * @param value The value to compare against .
     * @return Collection of states found.
     */
    fun filterByMetadata(entityManager: EntityManager, key: String, operation: Operation, value: Any): Collection<StateEntity>
}
