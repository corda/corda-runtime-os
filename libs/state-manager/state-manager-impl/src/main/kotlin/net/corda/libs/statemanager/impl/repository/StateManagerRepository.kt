package net.corda.libs.statemanager.impl.repository

import javax.persistence.EntityManager
import net.corda.libs.statemanager.impl.dto.StateDto

/**
 * Repository for entity operations on state manager entities.
 */
interface StateManagerRepository {
    /**
     * Get entities with the given keys.
     *
     * Transaction should be controlled by the caller.
     *
     * @param entityManager used to interact with the state manager persistence context.
     * @param keys collection of state keys to get entities for.
     * @return list of states found
     */
    fun get(entityManager: EntityManager, keys: Collection<String>): List<StateDto>

    /**
     * Put states states into the persistence context.
     *
     * Transaction should be controlled by the caller.
     *
     * @param entityManager used to interact with the state manager persistence context.
     * @param states collection of states to be persisted.
     * @return updated states successfully merged into the persistence context.
     */
    fun put(entityManager: EntityManager, states: Collection<StateDto>): List<StateDto>
}