package net.corda.libs.statemanager.impl.repository.impl

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.libs.statemanager.api.CompressionType
import net.corda.libs.statemanager.api.IntervalFilter
import net.corda.libs.statemanager.api.MetadataFilter
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.impl.compression.CompressionService
import net.corda.libs.statemanager.impl.model.v1.resultSetAsStateCollection
import net.corda.libs.statemanager.impl.repository.StateRepository
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.Timestamp
import java.time.Instant
import java.util.Calendar
import java.util.TimeZone

class StateRepositoryImpl(
    private val queryProvider: QueryProvider,
    private val compressionService: CompressionService,
    private val compressionType: CompressionType
) : StateRepository {

    private companion object {
        private val objectMapper = ObjectMapper()
        private const val CREATE_RESULT_COLUMN_INDEX = 1
    }

    /**
     * Always use same [TimeZone] as the default one used by the [queryProvider] instance.
     */
    private fun PreparedStatement.setTimestamp(parameterIndex: Int, instant: Instant) {
        setTimestamp(parameterIndex, Timestamp.from(instant), Calendar.getInstance(queryProvider.timeZone))
    }

    override fun create(connection: Connection, states: Collection<State>): Collection<String> {
        if (states.isEmpty()) return emptySet()
        return connection.prepareStatement(queryProvider.createStates(states.size)).use { statement ->
            val indices = generateSequence(1) { it + 1 }.iterator()
            states.forEach { state ->
                statement.setString(indices.next(), state.key)
                statement.setBytes(indices.next(), compressionService.writeBytes(state.value, compressionType))
                statement.setInt(indices.next(), state.version)
                statement.setString(indices.next(), objectMapper.writeValueAsString(state.metadata))
            }
            statement.execute()
            val results = statement.resultSet
            sequence<String> {
                while (results.next()) {
                    yield(results.getString(CREATE_RESULT_COLUMN_INDEX))
                }
            }.toList()
        }
    }

    override fun get(connection: Connection, keys: Collection<String>): Collection<State> {
        if (keys.isEmpty()) return emptySet()
        return connection.prepareStatement(queryProvider.findStatesByKey(keys.size)).use {
            keys.forEachIndexed { index, key ->
                it.setString(index + 1, key)
            }

            it.executeQuery().resultSetAsStateCollection(objectMapper, compressionService)
        }
    }

    override fun update(connection: Connection, states: Collection<State>): StateRepository.StateUpdateSummary {
        if (states.isEmpty()) return StateRepository.StateUpdateSummary(emptyList(), emptyList())
        val indices = generateSequence(1) { it + 1 }.iterator()
        val updatedKeys = mutableListOf<String>()
        connection.prepareStatement(queryProvider.updateStates(states.size)).use { stmt ->
            states.forEach { state ->
                stmt.setString(indices.next(), state.key)
                stmt.setBytes(indices.next(), compressionService.writeBytes(state.value, compressionType))
                stmt.setString(indices.next(), objectMapper.writeValueAsString(state.metadata))
                stmt.setInt(indices.next(), state.version)
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

    override fun delete(connection: Connection, states: Collection<State>): Collection<String> {
        if (states.isEmpty()) return emptySet()
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

    override fun updatedBetween(connection: Connection, interval: IntervalFilter): Collection<State> =
        connection.prepareStatement(queryProvider.findStatesUpdatedBetween).use {
            it.setTimestamp(1, interval.start)
            it.setTimestamp(2, interval.finish)
            it.executeQuery().resultSetAsStateCollection(objectMapper, compressionService)
        }

    override fun filterByAll(connection: Connection, filters: Collection<MetadataFilter>) =
        connection.prepareStatement(queryProvider.findStatesByMetadataMatchingAll(filters)).use {
            it.executeQuery().resultSetAsStateCollection(objectMapper, compressionService)
        }

    override fun filterByAny(connection: Connection, filters: Collection<MetadataFilter>) =
        connection.prepareStatement(queryProvider.findStatesByMetadataMatchingAny(filters)).use {
            it.executeQuery().resultSetAsStateCollection(objectMapper, compressionService)
        }

    override fun filterByUpdatedBetweenWithMetadataMatchingAll(
        connection: Connection,
        interval: IntervalFilter,
        filters: Collection<MetadataFilter>
    ) = connection.prepareStatement(queryProvider.findStatesUpdatedBetweenWithMetadataMatchingAll(filters)).use {
        it.setTimestamp(1, interval.start)
        it.setTimestamp(2, interval.finish)
        it.executeQuery().resultSetAsStateCollection(objectMapper, compressionService)
    }

    override fun filterByUpdatedBetweenWithMetadataMatchingAny(
        connection: Connection,
        interval: IntervalFilter,
        filters: Collection<MetadataFilter>
    ) = connection.prepareStatement(queryProvider.findStatesUpdatedBetweenWithMetadataMatchingAny(filters)).use {
        it.setTimestamp(1, interval.start)
        it.setTimestamp(2, interval.finish)
        it.executeQuery().resultSetAsStateCollection(objectMapper, compressionService)
    }
}
