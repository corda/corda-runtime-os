package net.corda.libs.statemanager.impl.repository.impl

import net.corda.libs.statemanager.impl.model.v1.CREATE_STATE_QUERY_NAME
import net.corda.libs.statemanager.impl.model.v1.DELETE_STATES_BY_KEY_QUERY_NAME
import net.corda.libs.statemanager.impl.model.v1.FINISH_TIMESTAMP
import net.corda.libs.statemanager.impl.model.v1.KEY_ID
import net.corda.libs.statemanager.impl.model.v1.METADATA_ID
import net.corda.libs.statemanager.impl.model.v1.QUERY_STATES_BY_KEY_QUERY_NAME
import net.corda.libs.statemanager.impl.model.v1.QUERY_STATES_BY_UPDATED_TIMESTAMP_NAME
import net.corda.libs.statemanager.impl.model.v1.START_TIMESTAMP
import net.corda.libs.statemanager.impl.model.v1.VALUE_ID
import net.corda.libs.statemanager.impl.model.v1.StateEntity
import net.corda.libs.statemanager.impl.model.v1.UPDATE_STATE_QUERY_NAME
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
        val results = keys.chunked(50) { chunkedKeys ->
            entityManager
                .createNamedQuery(QUERY_STATES_BY_KEY_QUERY_NAME.trimIndent(), StateEntity::class.java)
                .setParameter(KEY_ID, chunkedKeys)
                .resultList
        }

        return results.flatten()
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
                    .setParameter(VERSION_ID, it.version)
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
            .createNamedQuery(QUERY_STATES_BY_UPDATED_TIMESTAMP_NAME.trimIndent(), StateEntity::class.java)
            .setParameter(START_TIMESTAMP, start)
            .setParameter(FINISH_TIMESTAMP, finish)
            .resultList
    }
}
