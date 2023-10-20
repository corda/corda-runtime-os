package net.corda.libs.statemanager.impl.repository.impl

import java.sql.Connection
import javax.persistence.EntityManager
import javax.persistence.Query
import net.corda.libs.statemanager.api.IntervalFilter
import net.corda.libs.statemanager.api.MetadataFilter
import net.corda.libs.statemanager.impl.model.v1.StateEntity
import net.corda.libs.statemanager.impl.repository.StateRepository
import net.corda.libs.statemanager.impl.repository.impl.PreparedStatementHelper.extractFailedKeysFromBatchResults

class StateRepositoryImpl(private val queryProvider: QueryProvider) : StateRepository {

    @Suppress("UNCHECKED_CAST")
    private fun Query.resultListAsStateEntityCollection() = resultList as Collection<StateEntity>

    override fun create(connection: Connection, states: Collection<StateEntity>): Collection<String> {
        return connection.prepareStatement(queryProvider.createState).use { preparedStatement ->
            for (s in states) {
                preparedStatement.setString(1, s.key)
                preparedStatement.setBytes(2, s.value)
                preparedStatement.setInt(3, s.version)
                preparedStatement.setString(4, s.metadata)
                preparedStatement.addBatch()
            }
            extractFailedKeysFromBatchResults(preparedStatement.executeBatch(), states.map { it.key })
        }
    }

    override fun get(entityManager: EntityManager, keys: Collection<String>) =
        entityManager
            .createNativeQuery(queryProvider.findStatesByKey, StateEntity::class.java)
            .setParameter(KEYS_PARAMETER_NAME, keys)
            .resultListAsStateEntityCollection()

    override fun update(connection: Connection, states: Collection<StateEntity>): Collection<String> {
        return connection.prepareStatement(queryProvider.updateState).use { preparedStatement ->
            for (s in states) {
                preparedStatement.setString(1, s.key)
                preparedStatement.setBytes(2, s.value)
                preparedStatement.setString(3, s.metadata)
                preparedStatement.setString(4, s.key)
                preparedStatement.setInt(5, s.version)
                preparedStatement.addBatch()
            }
            val results = preparedStatement.executeBatch()
            extractFailedKeysFromBatchResults(results, states.map { it.key })
        }
    }

    override fun delete(connection: Connection, states: Collection<StateEntity>): Collection<String> {
        return connection.prepareStatement(queryProvider.deleteStatesByKey).use { preparedStatement ->
            for (s in states) {
                preparedStatement.setString(1, s.key)
                preparedStatement.setInt(2, s.version)
                preparedStatement.addBatch()
            }
            val results = preparedStatement.executeBatch()
            extractFailedKeysFromBatchResults(results, states.map { it.key })
        }
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
