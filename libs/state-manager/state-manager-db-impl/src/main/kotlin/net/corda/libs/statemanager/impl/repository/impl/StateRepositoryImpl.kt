package net.corda.libs.statemanager.impl.repository.impl

import net.corda.libs.statemanager.api.IntervalFilter
import net.corda.libs.statemanager.api.MetadataFilter
import net.corda.libs.statemanager.impl.model.v1.StateEntity
import net.corda.libs.statemanager.impl.model.v1.resultSetAsStateEntityCollection
import net.corda.libs.statemanager.impl.repository.StateRepository
import java.sql.Connection
import java.sql.Timestamp

// TODO-[CORE-18030]: batch create.
class StateRepositoryImpl(private val queryProvider: QueryProvider) : StateRepository {

    private companion object {
        private const val CREATE_NUM_PARAMETERS = 4
    }

    override fun create(connection: Connection, states: Collection<StateEntity>): Collection<String> {
        return connection.prepareStatement(queryProvider.createStates(states.size)).use { statement ->
            states.forEachIndexed { idx, state ->
                statement.setString(1 + (idx * CREATE_NUM_PARAMETERS), state.key)
                statement.setBytes(2 + (idx * CREATE_NUM_PARAMETERS), state.value)
                statement.setInt(3 + (idx * CREATE_NUM_PARAMETERS), state.version)
                statement.setString(4 + (idx * CREATE_NUM_PARAMETERS), state.metadata)
            }
            statement.execute()
            val results = statement.resultSet
            sequence<String> {
                while (results.next()) {
                    yield(results.getString(1))
                }
            }.toList()
        }
    }

    override fun get(connection: Connection, keys: Collection<String>) =
        connection.prepareStatement(queryProvider.findStatesByKey(keys.size)).use {
            keys.forEachIndexed { index, key ->
                it.setString(index + 1, key)
            }

            it.executeQuery().resultSetAsStateEntityCollection()
        }

    override fun update(connection: Connection, states: List<StateEntity>): StateRepository.StateUpdateSummary {
        fun getParameterIndex(currentRow: Int, index: Int) = (currentRow * 4) + index // 4 columns in the temp table

        if (states.isEmpty()) return StateRepository.StateUpdateSummary(emptyList(), emptyList())
        val updatedKeys = mutableListOf<String>()
        connection.prepareStatement(queryProvider.updateStates(states.size)).use { stmt ->
            repeat(states.size) { stateIterator ->
                stmt.setString(getParameterIndex(stateIterator, 1), states[stateIterator].key)
                stmt.setBytes(getParameterIndex(stateIterator, 2), states[stateIterator].value)
                stmt.setString(getParameterIndex(stateIterator, 3), states[stateIterator].metadata)
                stmt.setInt(getParameterIndex(stateIterator, 4), states[stateIterator].version)
            }
            stmt.execute()
            val results = stmt.resultSet
            while (results.next()) {
                updatedKeys.add(results.getString(1))
            }
        }
        return StateRepository.StateUpdateSummary(
            updatedKeys,
            states.map { it.key }.filterNot { updatedKeys.contains(it) }
        )
    }

    override fun delete(connection: Connection, states: Collection<StateEntity>): Collection<String> {
        return connection.prepareStatement(queryProvider.deleteStatesByKey).use { statement ->
            // The actual state order doesn't matter, but we must ensure that the states are iterated over in the same
            // order when examining the result as when the statements were generated.
            val statesOrdered = states.toList()
            statesOrdered.forEach { state ->
                statement.setString(1, state.key)
                statement.setInt(2, state.version)
                statement.addBatch()
            }
            // For the delete case, it's safe to return anything other than a row update count of 1 as failed. The state
            // manager must check any returned failed deletes regardless to verify that the call did not request
            // removal of a state that never existed.
            statement.executeBatch().zip(statesOrdered).mapNotNull { (count, state) ->
                if (count <= 0) {
                    state.key
                } else {
                    null
                }
            }.toList()
        }
    }

    override fun updatedBetween(connection: Connection, interval: IntervalFilter): Collection<StateEntity> =
        connection.prepareStatement(queryProvider.findStatesUpdatedBetween).use {
            it.setTimestamp(1, Timestamp.from(interval.start))
            it.setTimestamp(2, Timestamp.from(interval.finish))
            it.executeQuery().resultSetAsStateEntityCollection()
        }

    override fun filterByAll(connection: Connection, filters: Collection<MetadataFilter>) =
        connection.prepareStatement(queryProvider.findStatesByMetadataMatchingAll(filters)).use {
            it.executeQuery().resultSetAsStateEntityCollection()
        }

    override fun filterByAny(connection: Connection, filters: Collection<MetadataFilter>) =
        connection.prepareStatement(queryProvider.findStatesByMetadataMatchingAny(filters)).use {
            it.executeQuery().resultSetAsStateEntityCollection()
        }

    override fun filterByUpdatedBetweenWithMetadataMatchingAll(
        connection: Connection,
        interval: IntervalFilter,
        filters: Collection<MetadataFilter>
    ) = connection.prepareStatement(queryProvider.findStatesUpdatedBetweenWithMetadataMatchingAll(filters)).use {
        it.setTimestamp(1, Timestamp.from(interval.start))
        it.setTimestamp(2, Timestamp.from(interval.finish))
        it.executeQuery().resultSetAsStateEntityCollection()
    }

    override fun filterByUpdatedBetweenWithMetadataMatchingAny(
        connection: Connection,
        interval: IntervalFilter,
        filters: Collection<MetadataFilter>
    ) = connection.prepareStatement(queryProvider.findStatesUpdatedBetweenWithMetadataMatchingAny(filters)).use {
        it.setTimestamp(1, Timestamp.from(interval.start))
        it.setTimestamp(2, Timestamp.from(interval.finish))
        it.executeQuery().resultSetAsStateEntityCollection()
    }
}
