package net.corda.libs.statemanager.impl.repository.impl

import java.sql.Connection
import javax.persistence.EntityManager
import javax.persistence.Query
import net.corda.libs.statemanager.api.IntervalFilter
import net.corda.libs.statemanager.api.MetadataFilter
import net.corda.libs.statemanager.impl.model.v1.StateEntity
import net.corda.libs.statemanager.impl.repository.StateRepository

// TODO-[CORE-17733]: batch update and delete.
class StateRepositoryImpl(private val queryProvider: QueryProvider) : StateRepository {

    @Suppress("UNCHECKED_CAST")
    private fun Query.resultListAsStateEntityCollection() = resultList as Collection<StateEntity>

    override fun create(entityManager: EntityManager, state: StateEntity) {
        entityManager
            .createNativeQuery(queryProvider.createState)
            .setParameter(KEY_PARAMETER_NAME, state.key)
            .setParameter(VALUE_PARAMETER_NAME, state.value)
            .setParameter(VERSION_PARAMETER_NAME, state.version)
            .setParameter(METADATA_PARAMETER_NAME, state.metadata)
            .executeUpdate()
    }

    override fun get(entityManager: EntityManager, keys: Collection<String>) =
        entityManager
            .createNativeQuery(queryProvider.findStatesByKey, StateEntity::class.java)
            .setParameter(KEYS_PARAMETER_NAME, keys)
            .resultListAsStateEntityCollection()

    override fun update(connection: Connection, states: List<StateEntity>): StateRepository.StateUpdateSummary {
        fun getParameterIndex(currentRow:Int, index: Int) = (currentRow * 4) + index // 4 columns in the temp table
        val updatedKeys = mutableListOf<String>()
        connection.prepareStatement(queryProvider.updateStates(states)).use { stmt ->
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

    override fun delete(entityManager: EntityManager, states: Collection<StateEntity>): Collection<String> {
        val failedKeys = mutableListOf<String>()

        states.forEach { state ->
            entityManager
                .createNativeQuery(queryProvider.deleteStatesByKey)
                .setParameter(KEY_PARAMETER_NAME, state.key)
                .setParameter(VERSION_PARAMETER_NAME, state.version)
                .executeUpdate().also {
                    if (it == 0) {
                        failedKeys.add(state.key)
                    }
                }
        }

        return failedKeys
    }

    override fun updatedBetween(entityManager: EntityManager, interval: IntervalFilter): Collection<StateEntity> =
        entityManager
            .createNativeQuery(queryProvider.findStatesUpdatedBetween, StateEntity::class.java)
            .setParameter(START_TIMESTAMP_PARAMETER_NAME, interval.start)
            .setParameter(FINISH_TIMESTAMP_PARAMETER_NAME, interval.finish)
            .resultListAsStateEntityCollection()

    override fun filterByAll(entityManager: EntityManager, filters: Collection<MetadataFilter>) =
        entityManager
            .createNativeQuery(queryProvider.findStatesByMetadataMatchingAll(filters), StateEntity::class.java)
            .resultListAsStateEntityCollection()

    override fun filterByAny(entityManager: EntityManager, filters: Collection<MetadataFilter>) =
        entityManager
            .createNativeQuery(queryProvider.findStatesByMetadataMatchingAny(filters), StateEntity::class.java)
            .resultListAsStateEntityCollection()

    override fun filterByUpdatedBetweenAndMetadata(
        entityManager: EntityManager,
        interval: IntervalFilter,
        filter: MetadataFilter
    ) = entityManager
        .createNativeQuery(
            queryProvider.findStatesUpdatedBetweenAndFilteredByMetadataKey(filter),
            StateEntity::class.java
        )
        .setParameter(START_TIMESTAMP_PARAMETER_NAME, interval.start)
        .setParameter(FINISH_TIMESTAMP_PARAMETER_NAME, interval.finish)
        .resultListAsStateEntityCollection()
}
