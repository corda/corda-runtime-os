package net.corda.libs.statemanager.impl.repository.impl

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.libs.statemanager.api.IntervalFilter
import net.corda.libs.statemanager.api.Metadata
import net.corda.libs.statemanager.api.MetadataFilter
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.impl.model.v1.StateColumns
import net.corda.libs.statemanager.impl.repository.StateRepository
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp

class StateRepositoryImpl(private val queryProvider: QueryProvider) : StateRepository {

    private companion object {
        private const val CREATE_RESULT_COLUMN_INDEX = 1
        private val objectMapper = ObjectMapper()
    }

    override fun create(connection: Connection, states: Collection<State>): Collection<String> {
        if (states.isEmpty()) return emptySet()

        val metaCount = states.sumOf { it.metadata.entries.size }
        connection.prepareStatement(queryProvider.createMetadataStates(metaCount)).use { metadataStatement ->
            val metadataIndices = generateSequence(1) { it + 1 }.iterator()
            states.flatMap { state ->
                state.metadata.map { (key, value) ->
                    metadataStatement.setString(metadataIndices.next(), state.key)
                    metadataStatement.setString(metadataIndices.next(), key)
                    metadataStatement.setString(metadataIndices.next(), value.toString())
                    metadataStatement.setInt(metadataIndices.next(), state.version)
                }
            }
            metadataStatement.execute()
        }

        return connection.prepareStatement(queryProvider.createStates(states.size)).use { statement ->
            val indices = generateSequence(1) { it + 1 }.iterator()
            states.forEach { state ->
                statement.setString(indices.next(), state.key)
                statement.setBytes(indices.next(), state.value)
                statement.setInt(indices.next(), state.version)
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

        val query = queryProvider.findStatesByKey(keys.size)
        return connection.prepareStatement(query).use { statement ->
            keys.forEachIndexed { index, key ->
                statement.setString(index + 1, key)
            }

            val resultSet = statement.executeQuery()
            resultSet.resultSetAsStateCollection(objectMapper)
        }
    }

    private fun ResultSet.resultSetAsMetadataCollection(): Map<String, Map<String, Any>> {
        val metadataMap = mutableMapOf<String, MutableMap<String, Any>>()

        while (next()) {
            val stateKey = getString("statekey")
            val key = getString("key")
            val value = getString("value")

            metadataMap.computeIfAbsent(stateKey) { mutableMapOf() }[key] = value
        }

        return metadataMap
    }

    @Suppress("unused_parameter")
    private fun ResultSet.resultSetAsStateCollection(objectMapper: ObjectMapper): Collection<State> {
        val stateMap = mutableMapOf<String, State>()

        while (next()) {
            val key = getString(StateColumns.KEY_COLUMN)
            val value = getBytes(StateColumns.VALUE_COLUMN)
            val version = getInt(StateColumns.VERSION_COLUMN)
            val modifiedTime = getTimestamp(StateColumns.MODIFIED_TIME_COLUMN).toInstant()

            val state = stateMap[key] ?: State(key, value, version, Metadata(mutableMapOf()), modifiedTime)
            stateMap[key] = state

            // Check if metadata exists
            val metadataKey = getString("metadata_key")
            if (!metadataKey.isNullOrBlank()) {
                val metadataValue = getString("metadata_value")
                stateMap[key] = state.copy(metadata = Metadata(state.metadata + (metadataKey to metadataValue)))
            }
        }

        return stateMap.values.toList()
    }

    override fun update(connection: Connection, states: Collection<State>): StateRepository.StateUpdateSummary {
        if (states.isEmpty()) return StateRepository.StateUpdateSummary(emptyList(), emptyList())
        val indices = generateSequence(1) { it + 1 }.iterator()
        val updatedKeys = mutableListOf<String>()
        val metaCount = states.sumOf { it.metadata.entries.size }

        connection.prepareStatement(queryProvider.updateStates(states.size)).use { stmt ->
            states.forEach { state ->
                stmt.setString(indices.next(), state.key)
                stmt.setBytes(indices.next(), state.value)
                stmt.setInt(indices.next(), state.version)
            }

            stmt.execute()
            val results = stmt.resultSet
            while (results.next()) {
                updatedKeys.add(results.getString(1))
            }
        }

        val updatedStates = states.filter { updatedKeys.contains(it.key) }
        if (updatedStates.isNotEmpty() && updatedStates.any { it.metadata.isNotEmpty() }) {
            connection.prepareStatement(queryProvider.createMetadataStates(metaCount)).use { metadataStatement ->
                val metadataIndices = generateSequence(1) { it + 1 }.iterator()
                updatedStates.flatMap { state ->
                    state.metadata.map { (key, value) ->
                        metadataStatement.setString(metadataIndices.next(), state.key)
                        metadataStatement.setString(metadataIndices.next(), key)
                        metadataStatement.setString(metadataIndices.next(), value.toString())
                        metadataStatement.setInt(metadataIndices.next(), state.version + 1)
                    }
                }
                metadataStatement.execute()
            }
        }

        return StateRepository.StateUpdateSummary(
            updatedKeys,
            states.map { it.key }.filterNot { updatedKeys.contains(it) }
        )
    }

    override fun delete(connection: Connection, states: Collection<State>): Collection<String> {
        if (states.isEmpty()) return emptySet()

        connection.prepareStatement(queryProvider.deleteMetaStatesByKey).use { statement ->
            // The actual state order doesn't matter, but we must ensure that the states are iterated over in the same
            // order when examining the result as when the statements were generated.
            val statesOrdered = states.toList()
            statesOrdered.forEach { state ->
                statement.setString(1, state.key)
                statement.addBatch()
            }
            statement.execute()
        }

        return connection.prepareStatement(queryProvider.deleteStatesByKey).use { statement ->
            // The actual state order doesn't matter, but we must ensure that the states are iterated over in the same
            // order when examining the result as when the statements were generated.
            val statesOrdered = states.toList()
            statesOrdered.forEach { state ->
                statement.setString(1, state.key)
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
            it.setTimestamp(1, Timestamp.from(interval.start))
            it.setTimestamp(2, Timestamp.from(interval.finish))
            it.executeQuery().resultSetAsStateCollection(objectMapper)
        }

    override fun filterByAll(connection: Connection, filters: Collection<MetadataFilter>) =
        connection.prepareStatement(queryProvider.findStatesByMetadataMatchingAll(filters)).use {
            it.executeQuery().resultSetAsStateCollection(objectMapper)
        }

    override fun filterByAny(connection: Connection, filters: Collection<MetadataFilter>) =
        connection.prepareStatement(queryProvider.findStatesByMetadataMatchingAny(filters)).use {
            it.executeQuery().resultSetAsStateCollection(objectMapper)
        }

    override fun filterByUpdatedBetweenWithMetadataMatchingAll(
        connection: Connection,
        interval: IntervalFilter,
        filters: Collection<MetadataFilter>
    ) = connection.prepareStatement(queryProvider.findStatesUpdatedBetweenWithMetadataMatchingAll(filters)).use {
        it.setTimestamp(1, Timestamp.from(interval.start))
        it.setTimestamp(2, Timestamp.from(interval.finish))
        it.executeQuery().resultSetAsStateCollection(objectMapper)
    }

    override fun filterByUpdatedBetweenWithMetadataMatchingAny(
        connection: Connection,
        interval: IntervalFilter,
        filters: Collection<MetadataFilter>
    ) = connection.prepareStatement(queryProvider.findStatesUpdatedBetweenWithMetadataMatchingAny(filters)).use {
        it.setTimestamp(1, Timestamp.from(interval.start))
        it.setTimestamp(2, Timestamp.from(interval.finish))
        it.executeQuery().resultSetAsStateCollection(objectMapper)
    }
}
