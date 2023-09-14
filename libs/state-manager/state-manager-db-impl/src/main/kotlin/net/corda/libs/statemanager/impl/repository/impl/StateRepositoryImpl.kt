package net.corda.libs.statemanager.impl.repository.impl

import net.corda.db.schema.DbSchema
import net.corda.libs.statemanager.api.Operation
import net.corda.libs.statemanager.impl.model.v1.CREATE_STATE_QUERY_NAME
import net.corda.libs.statemanager.impl.model.v1.DELETE_STATES_BY_KEY_QUERY_NAME
import net.corda.libs.statemanager.impl.model.v1.FILTER_STATES_BY_KEY_QUERY_NAME
import net.corda.libs.statemanager.impl.model.v1.FILTER_STATES_BY_UPDATED_TIMESTAMP_QUERY_NAME
import net.corda.libs.statemanager.impl.model.v1.FINISH_TIMESTAMP_ID
import net.corda.libs.statemanager.impl.model.v1.KEY_ID
import net.corda.libs.statemanager.impl.model.v1.METADATA_ID
import net.corda.libs.statemanager.impl.model.v1.START_TIMESTAMP_ID
import net.corda.libs.statemanager.impl.model.v1.StateEntity
import net.corda.libs.statemanager.impl.model.v1.UPDATE_STATE_QUERY_NAME
import net.corda.libs.statemanager.impl.model.v1.VALUE_ID
import net.corda.libs.statemanager.impl.model.v1.VERSION_ID
import net.corda.libs.statemanager.impl.repository.StateRepository
import org.slf4j.LoggerFactory
import java.time.Instant
import javax.persistence.EntityManager

class StateRepositoryImpl : StateRepository {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun create(entityManager: EntityManager, state: StateEntity) {
        entityManager
            .createNamedQuery(CREATE_STATE_QUERY_NAME.trimIndent())
            .setParameter(KEY_ID, state.key)
            .setParameter(VALUE_ID, state.value)
            .setParameter(VERSION_ID, state.version)
            .setParameter(METADATA_ID, state.metadata)
            .executeUpdate()
    }

    private fun findByKeys(
        entityManager: EntityManager,
        keys: Collection<String>
    ): List<StateEntity> {
        return entityManager
                .createNamedQuery(FILTER_STATES_BY_KEY_QUERY_NAME.trimIndent(), StateEntity::class.java)
                .setParameter(KEY_ID, keys)
                .resultList
    }

    override fun get(entityManager: EntityManager, keys: Collection<String>): List<StateEntity> {
        return findByKeys(entityManager, keys)
    }

    override fun update(entityManager: EntityManager, states: Collection<StateEntity>) {
        try {
            states.forEach {
                entityManager
                    .createNamedQuery(UPDATE_STATE_QUERY_NAME.trimIndent())
                    .setParameter(KEY_ID, it.key)
                    .setParameter(VALUE_ID, it.value)
                    .setParameter(METADATA_ID, it.metadata)
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
                .createNamedQuery(DELETE_STATES_BY_KEY_QUERY_NAME.trimIndent())
                .setParameter(KEY_ID, keys)
                .executeUpdate()
        } catch (e: Exception) {
            logger.warn("Failed to delete batch of states - ${keys.joinToString()}", e)
            throw e
        }
    }

    override fun findUpdatedBetween(
        entityManager: EntityManager,
        start: Instant,
        finish: Instant
    ): Collection<StateEntity> {
        return entityManager
            .createNamedQuery(FILTER_STATES_BY_UPDATED_TIMESTAMP_QUERY_NAME.trimIndent(), StateEntity::class.java)
            .setParameter(START_TIMESTAMP_ID, start)
            .setParameter(FINISH_TIMESTAMP_ID, finish)
            .resultList
    }

    override fun filterByMetadata(
        entityManager: EntityManager,
        key: String,
        operation: Operation,
        value: Any
    ): Collection<StateEntity> {
        // Comparison operation to execute
        val comparison = when (operation) {
            Operation.Equals -> "="
            Operation.NotEquals -> "<>"
            Operation.LesserThan -> "<"
            Operation.GreaterThan -> ">"
        }

        // Only primitive types are supported as part of the state metadata
        val nativeType = when (value) {
            is String -> "text"
            is Number -> "numeric"
            is Boolean -> "boolean"
            else -> throw IllegalArgumentException("Unsupported Type: ${value::class.java.simpleName}")
        }
        val query = entityManager.createNativeQuery(
            "SELECT s.key, s.value, s.metadata, s.version, s.modified_time " +
                    "FROM ${DbSchema.STATE_MANAGER_TABLE} s " +
                    "WHERE (s.metadata->>'$key')::::$nativeType $comparison '$value'",
            StateEntity::class.java
        )

        @Suppress("UNCHECKED_CAST")
        return query.resultList as Collection<StateEntity>
    }
}
