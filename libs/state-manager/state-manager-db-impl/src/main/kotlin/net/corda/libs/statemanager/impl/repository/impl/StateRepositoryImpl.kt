package net.corda.libs.statemanager.impl.repository.impl

import net.corda.libs.statemanager.api.IntervalFilter
import net.corda.libs.statemanager.api.MetadataFilter
import net.corda.libs.statemanager.impl.model.v1.StateEntity
import net.corda.libs.statemanager.impl.repository.StateRepository
import org.slf4j.LoggerFactory
import javax.persistence.EntityManager
import javax.persistence.Query

class StateRepositoryImpl(private val queryProvider: QueryProvider) : StateRepository {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

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

    override fun update(entityManager: EntityManager, states: Collection<StateEntity>) =
        try {
            states.forEach {
                entityManager
                    .createNativeQuery(queryProvider.updateState)
                    .setParameter(KEY_PARAMETER_NAME, it.key)
                    .setParameter(VALUE_PARAMETER_NAME, it.value)
                    .setParameter(METADATA_PARAMETER_NAME, it.metadata)
                    .executeUpdate()
            }
        } catch (e: Exception) {
            logger.warn("Failed to updated batch of states - ${states.joinToString { it.key }}", e)
            throw e
        }

    override fun delete(entityManager: EntityManager, keys: Collection<String>) {
        try {
            entityManager
                .createNativeQuery(queryProvider.deleteStatesByKey)
                .setParameter(KEYS_PARAMETER_NAME, keys)
                .executeUpdate()
        } catch (e: Exception) {
            logger.warn("Failed to delete batch of states - ${keys.joinToString()}", e)
            throw e
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
