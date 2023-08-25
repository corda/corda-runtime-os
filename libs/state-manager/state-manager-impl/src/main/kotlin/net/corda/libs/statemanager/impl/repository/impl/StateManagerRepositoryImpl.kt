package net.corda.libs.statemanager.impl.repository.impl

import javax.persistence.EntityManager
import net.corda.db.schema.DbSchema
import net.corda.libs.statemanager.impl.dto.StateDto
import net.corda.libs.statemanager.impl.model.v1.StateEntity
import net.corda.libs.statemanager.impl.repository.StateManagerRepository
import org.slf4j.LoggerFactory

class StateManagerRepositoryImpl : StateManagerRepository {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun get(entityManager: EntityManager, keys: Collection<String>): List<StateDto> {
        val results = findByKeys(entityManager, keys)
        return results
            .map { it.toDto() }
    }

    private fun findByKeys(
        entityManager: EntityManager,
        keys: Collection<String>
    ): List<StateEntity> {
        val query = "FROM ${StateEntity::class.simpleName} s where s.key IN :keys"
        val results = keys.chunked(50) { chunkedKeys ->
            entityManager.createQuery(query, StateEntity::class.java)
                .setParameter("keys", chunkedKeys)
                .resultList
        }
        return results.flatten()
    }

    override fun put(entityManager: EntityManager, states: Collection<StateDto>) {
        try {
            states.forEach {
                persistWithNativeQuery(entityManager, it)
            }
        } catch (e: Exception) {
            logger.warn("Failed to persist batch of states - ${states.joinToString { it.key }}", e)
            throw e
        }
    }

    // hibernate 5 does not support inserting a String to a jsonb column type out of the box. Native query with casting is also used
    // in the ledger. It means, however, this does not work with HSQLDB. Thus, integration tests are skipped when using this in-memory
    // database.
    private fun persistWithNativeQuery(entityManager: EntityManager, it: StateDto) {
        entityManager.createNativeQuery(
            """
                INSERT INTO ${DbSchema.STATE_MANAGER_TABLE}
                VALUES (
                    :key,
                    :state,
                    :version,
                    CAST(:metadata as JSONB),
                    CURRENT_TIMESTAMP
                )
                ON CONFLICT DO NOTHING
            """.trimIndent()
        )
            .setParameter("key", it.key)
            .setParameter("state", it.state)
            .setParameter("version", it.version)
            .setParameter("metadata", it.metadata)
            .executeUpdate()
    }

    private fun StateEntity.toDto() = StateDto(key, state, version!!, metadata, modifiedTime!!)
}