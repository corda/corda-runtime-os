package net.corda.libs.statemanager.impl.repository

import net.corda.libs.statemanager.api.IntervalFilter
import net.corda.libs.statemanager.api.MetadataFilter
import net.corda.libs.statemanager.impl.model.v1.StateEntity
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
     * Create state into the persistence context.
     * Transaction should be controlled by the caller.
     *
     * @param connection The JDBC connection used to interact with the database.
     * @param state State entity to persist.
     */
    fun create(connection: Connection, state: StateEntity)

    /**
     * Get states with the given keys.
     * Transaction should be controlled by the caller.
     *
     * @param connection The JDBC connection used to interact with the database.
     * @param keys Collection of state keys to get entities for.
     * @return Collection of states found.
     */
    fun get(connection: Connection, keys: Collection<String>): Collection<StateEntity>

    /**
     * Update a collection of states within the database using JDBC connection.
     *
     * Note: Transaction should be controlled by the caller.
     *
     * @param connection The JDBC connection used to interact with the database.
     * @param states A collection of states to be updated in the database.
     * @return State keys for both successful and failed updates where states could not be updated due to optimistic locking check failure.
     */
    fun update(connection: Connection, states: List<StateEntity>): StateUpdateSummary

    /**
     * Delete states with the given keys from the persistence context.
     * Transaction should be controlled by the caller.
     *
     * @param connection The JDBC connection used to interact with the database.
     * @param states Collection of states to be deleted.
     * @return Collection of keys for states that could not be deleted due to optimistic locking check failure.
     */
    fun delete(connection: Connection, states: Collection<StateEntity>): Collection<String>

    /**
     * Retrieve entities that were lastly updated between [IntervalFilter.start] and [IntervalFilter.finish].
     * Transaction should be controlled by the caller.
     *
     * @param connection The JDBC connection used to interact with the database.
     * @param interval Lower and upper bounds to use when filtering by last modified time.
     * @return Collection of states found.
     */
    fun updatedBetween(connection: Connection, interval: IntervalFilter): Collection<StateEntity>

    /**
     * Filter states based on a list of custom single key filters over the [StateEntity.metadata], only states matching
     * all [filters] are returned.
     * Transaction should be controlled by the caller.
     *
     * @param connection The JDBC connection used to interact with the database.
     * @param filters List of filter to use when searching for entities.
     * @return Collection of states found.
     */
    fun filterByAll(connection: Connection, filters: Collection<MetadataFilter>): Collection<StateEntity>

    /**
     * Filter states based on a list of custom single key filters over the [StateEntity.metadata], states matching
     * any of the [filters] are returned.
     * Transaction should be controlled by the caller.
     *
     * @param connection The JDBC connection used to interact with the database.
     * @param filters List of filter to use when searching for entities.
     * @return Collection of states found.
     */
    fun filterByAny(connection: Connection, filters: Collection<MetadataFilter>): Collection<StateEntity>

    /**
     * Filter states based on a custom comparison operation to be executed against a single key within the metadata and
     * the last updated time.
     * Transaction should be controlled by the caller.
     *
     * @param connection The JDBC connection used to interact with the database.
     * @param interval Lower and upper bound to use when filtering by time.
     * @param filter Filter to use when searching for entities.
     * @return Collection of states found.
     */
    @Suppress("LongParameterList")
    fun filterByUpdatedBetweenAndMetadata(
        connection: Connection,
        interval: IntervalFilter,
        filter: MetadataFilter
    ): Collection<StateEntity>
}
