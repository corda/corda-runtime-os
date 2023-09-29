package net.corda.libs.statemanager.impl.repository.impl

import net.corda.libs.statemanager.api.Operation
import net.corda.libs.statemanager.impl.model.v1.StateEntity
import net.corda.libs.statemanager.impl.repository.StateRepository
import org.slf4j.LoggerFactory
import java.time.Instant
import javax.persistence.EntityManager
import javax.persistence.Query

class StateRepositoryImpl(private val queryProvider: QueryProvider) : StateRepository {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @Suppress("UNCHECKED_CAST")
    private fun Query.resultListAsStateEntityCollection() = resultList as Collection<StateEntity>

    private fun findByKeys(
        entityManager: EntityManager,
        keys: Collection<String>
    ): Collection<StateEntity> {
        return entityManager
            .createNativeQuery(queryProvider.findStatesByKey, StateEntity::class.java)
            .setParameter(KEYS_PARAMETER_NAME, keys)
            .resultListAsStateEntityCollection()
    }

    override fun create(entityManager: EntityManager, state: StateEntity) {
        entityManager
            .createNativeQuery(queryProvider.createState)
            .setParameter(KEY_PARAMETER_NAME, state.key)
            .setParameter(VALUE_PARAMETER_NAME, state.value)
            .setParameter(VERSION_PARAMETER_NAME, state.version)
            .setParameter(METADATA_PARAMETER_NAME, state.metadata)
            .executeUpdate()
    }

    override fun get(entityManager: EntityManager, keys: Collection<String>): Collection<StateEntity> {
        return findByKeys(entityManager, keys)
    }

    override fun update(entityManager: EntityManager, states: Collection<StateEntity>) {
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

    override fun updatedBetween(
        entityManager: EntityManager,
        start: Instant,
        finish: Instant
    ): Collection<StateEntity> {
        return entityManager
            .createNativeQuery(queryProvider.findStatesUpdatedBetween, StateEntity::class.java)
            .setParameter(START_TIMESTAMP_PARAMETER_NAME, start)
            .setParameter(FINISH_TIMESTAMP_PARAMETER_NAME, finish)
            .resultListAsStateEntityCollection()
    }

    override fun filterByMetadata(
        entityManager: EntityManager,
        key: String, operation: Operation, value: Any
    ): Collection<StateEntity> {
        return entityManager
            .createNativeQuery(
                queryProvider.statesFilteredByMetadataKey(key, operation, value),
                StateEntity::class.java
            )
            .resultListAsStateEntityCollection()
    }

    override fun filterByUpdatedBetweenAndMetadata(
        entityManager: EntityManager,
        start: Instant,
        finish: Instant,
        key: String,
        operation: Operation,
        value: Any
    ): Collection<StateEntity> {
        return entityManager
            .createNativeQuery(
                queryProvider.statesUpdatedBetweenAndFilteredByMetadataKey(key, operation, value),
                StateEntity::class.java
            )
            .setParameter(START_TIMESTAMP_PARAMETER_NAME, start)
            .setParameter(FINISH_TIMESTAMP_PARAMETER_NAME, finish)
            .resultListAsStateEntityCollection()
    }
}
