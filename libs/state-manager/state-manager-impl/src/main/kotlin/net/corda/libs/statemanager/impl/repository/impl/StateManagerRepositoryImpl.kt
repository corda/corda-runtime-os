package net.corda.libs.statemanager.impl.repository.impl

import javax.persistence.EntityManager
import net.corda.libs.statemanager.impl.dto.StateDto
import net.corda.libs.statemanager.impl.model.v1_0.StateEntity
import net.corda.libs.statemanager.impl.repository.StateManagerRepository

class StateManagerRepositoryImpl : StateManagerRepository {

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

    override fun put(entityManager: EntityManager, states: Collection<StateDto>): List<StateDto> {
        val mergedEntities = states.map {
            entityManager.merge(it.toEntity())
        }
        return mergedEntities.map { it.toDto() }
    }

    private fun StateEntity.toDto() = StateDto(key, state, version!!, metadata, modifiedTime!!)
    private fun StateDto.toEntity() = StateEntity(key, state, version, metadata, modifiedTime)
}