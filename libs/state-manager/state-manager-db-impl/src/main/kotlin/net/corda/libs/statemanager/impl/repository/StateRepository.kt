package net.corda.libs.statemanager.impl.repository

import net.corda.libs.statemanager.api.IntervalFilter
import net.corda.libs.statemanager.api.MetadataFilter
import net.corda.libs.statemanager.api.State
import java.sql.Connection

/**
 * Repository for entity operations on state manager entities.
 */
interface StateRepository {

    /**
     * Response type after modification of State entities.
     *
     * @param successfulKeys the keys of states that were successfully updated
     * @param failedKeys the keys of states that were not updated
     */
    data class StateUpdateSummary(
        val successfulKeys: List<String>,
        val failedKeys: List<String>
    )

    /**
     * Create a collection of states.
     * Transaction should be controlled by the caller.
     *
     * @param connection The JDBC connection used to interact with the database.
     * @param states State entity to persist.
     * @return The collection of keys that were successfully created.
     */
    fun create(connection: Connection, states: Collection<State>): Collection<String>

    /**
     * Get states with the given keys.
     * Transaction should be controlled by the caller.
     *
     * @param connection The JDBC connection used to interact with the database.
     * @param keys Collection of state keys to get entities for.
     * @return Collection of states found.
     */
    fun get(connection: Connection, keys: Collection<String>): Collection<State>

    /**
     * Update collection of states.
     * Transaction should be controlled by the caller.
     *
     * @param connection The JDBC connection used to interact with the database.
     * @param states A collection of states to be updated in the database.
     * @return State keys for both successful and failed updates where states could not be updated due to optimistic locking check failure.
     */
    fun update(connection: Connection, states: Collection<State>): StateUpdateSummary

    /**
     * Delete states with the given keys.
     * Transaction should be controlled by the caller.
     *
     * Note that if the underlying provider isn't sure whether the delete was successful, the repository should behave
     * as if it failed. The state manager must double-check any failures reported by the repository.
     *
     * @param connection The JDBC connection used to interact with the database.
     * @param states Collection of states to be deleted.
     * @return Collection of keys for states that could not be deleted due to optimistic locking check failure.
     */
    fun delete(connection: Connection, states: Collection<State>): Collection<String>

    fun delete(connection: Connection, states: List<String>): Collection<String>

    /**
     * Retrieve states for which [State.modifiedTime] is within [interval].
     * Transaction should be controlled by the caller.
     *
     * @param connection The JDBC connection used to interact with the database.
     * @param interval Lower and upper bounds to use when filtering by last modified time.
     * @return Collection of states found.
     */
    fun updatedBetween(connection: Connection, interval: IntervalFilter): Collection<State>

    /**
     * Retrieve states exclusively matching all specified [filters] (comparisons are applied against the stored keys
     * and values within the [State.metadata]).
     * Transaction should be controlled by the caller.
     *
     * @param connection The JDBC connection used to interact with the database.
     * @param filters List of filter to use when searching for entities.
     * @return Collection of states found.
     */
    fun filterByAll(connection: Connection, filters: Collection<MetadataFilter>): Collection<State>

    /**
     * Retrieve states matching any of the specified [filters] (comparisons are applied against the stored keys and
     * values within the [State.metadata]).
     * Transaction should be controlled by the caller.
     *
     * @param connection The JDBC connection used to interact with the database.
     * @param filters List of filter to use when searching for entities.
     * @return Collection of states found.
     */
    fun filterByAny(connection: Connection, filters: Collection<MetadataFilter>): Collection<State>

    /**
     * Retrieve states that were lastly updated within [interval] (compared against [State.modifiedTime]) and
     * exclusively matching all specified [filters] (comparisons are applied against the stored keys and values within
     * the [State.metadata]).
     * Transaction should be controlled by the caller.
     *
     * @param connection The JDBC connection used to interact with the database.
     * @param interval Lower and upper bound to use when filtering by time.
     * @param filters List of filter to use when searching for entities.
     * @return Collection of states found.
     */
    fun filterByUpdatedBetweenWithMetadataMatchingAll(
        connection: Connection,
        interval: IntervalFilter,
        filters: Collection<MetadataFilter>
    ): Collection<State>

    /**
     * Retrieve states that were lastly updated within [interval] (compared against [State.modifiedTime]) and
     * matching any of the specified [filters] (comparisons are applied against the stored keys and values within
     * the [State.metadata]).
     * Transaction should be controlled by the caller.
     *
     * @param connection The JDBC connection used to interact with the database.
     * @param interval Lower and upper bound to use when filtering by time.
     * @param filters List of filter to use when searching for entities.
     * @return Collection of states found.
     */
    fun filterByUpdatedBetweenWithMetadataMatchingAny(
        connection: Connection,
        interval: IntervalFilter,
        filters: Collection<MetadataFilter>
    ): Collection<State>
}
