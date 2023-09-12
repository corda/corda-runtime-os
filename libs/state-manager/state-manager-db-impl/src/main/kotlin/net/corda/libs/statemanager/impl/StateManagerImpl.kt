package net.corda.libs.statemanager.impl

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.libs.statemanager.api.Operation
import net.corda.libs.statemanager.api.Metadata
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.libs.statemanager.impl.model.v1.StateEntity
import net.corda.libs.statemanager.impl.repository.StateRepository
import net.corda.orm.utils.transaction
import org.slf4j.LoggerFactory
import java.time.Instant
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory

// TODO-[CORE-17025]: remove Hibernate
// TODO-[CORE-16323]: remove current "hack" and implement proper optimistic locking
class StateManagerImpl(
    private val stateRepository: StateRepository,
    private val entityManagerFactory: EntityManagerFactory,
) : StateManager {

    private companion object {
        private val objectMapper = ObjectMapper()
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private fun State.toPersistentEntity(): StateEntity =
        StateEntity(key, value, objectMapper.writeValueAsString(metadata), version, modifiedTime)

    private fun StateEntity.fromPersistentEntity() =
        State(key, value, version, metadata.toMetadataMap(), modifiedTime)

    private fun String.toMetadataMap() =
        objectMapper.readValue(this, object : TypeReference<Metadata>() {})

    internal fun checkVersionAndPrepareEntitiesForPersistence(
        states: Collection<State>,
        entityManager: EntityManager
    ): Pair<List<StateEntity>, Map<String, State>> {
        val matchedVersions = mutableListOf<StateEntity>()
        val unmatchedVersions = mutableMapOf<String, State>()
        val persistedStates = stateRepository.get(entityManager, states.map { it.key })

        states.forEach { st ->
            val persisted = persistedStates.find { it.key == st.key }

            persisted?.let {
                if (st.version == persisted.version) {
                    matchedVersions.add(st.toPersistentEntity())
                } else {
                    unmatchedVersions[it.key] = it.fromPersistentEntity()
                }
            }
        }

        return Pair(matchedVersions, unmatchedVersions)
    }

    override fun create(states: Collection<State>): Map<String, Exception> {
        val failures = mutableMapOf<String, Exception>()

        states.map {
            it.toPersistentEntity()
        }.forEach {
            try {
                entityManagerFactory.transaction { em ->
                    stateRepository.create(em, it)
                }
            } catch (e: Exception) {
                logger.warn("Failed to create state with id ${it.key}", e)
                failures[it.key] = e
            }
        }

        return failures
    }

    override fun get(keys: Collection<String>): Map<String, State> {
        return entityManagerFactory.transaction { em ->
            stateRepository.get(em, keys)
        }.map {
            it.fromPersistentEntity()
        }.associateBy {
            it.key
        }
    }

    override fun update(states: Collection<State>): Map<String, State> {
        entityManagerFactory.transaction { em ->
            val (updatable, mismatchVersions) = checkVersionAndPrepareEntitiesForPersistence(states, em)
            stateRepository.update(em, updatable)

            return mismatchVersions.also {
                if (it.isNotEmpty()) {
                    logger.info("Optimistic locking check failed for States ${it.entries.joinToString()}")
                }
            }
        }
    }

    override fun delete(states: Collection<State>): Map<String, State> {
        entityManagerFactory.transaction { em ->
            val (deletable, mismatchVersions) = checkVersionAndPrepareEntitiesForPersistence(states, em)
            stateRepository.delete(em, deletable.map { it.key })

            return mismatchVersions.also {
                if (it.isNotEmpty()) {
                    logger.info("Optimistic locking check failed for States ${it.entries.joinToString()}")
                }
            }
        }
    }

    override fun getUpdatedBetween(start: Instant, finish: Instant): Map<String, State> {
        return entityManagerFactory.transaction { em ->
            stateRepository.findUpdatedBetween(em, start, finish)
        }
            .map { it.fromPersistentEntity() }
            .associateBy { it.key }
    }

    override fun find(key: String, operation: Operation, value: Any): Map<String, State> {
        return entityManagerFactory.transaction { em ->
            stateRepository.filterByMetadata(em, key, operation, value)
        }.map {
            it.fromPersistentEntity()
        }.associateBy {
            it.key
        }
    }

    override fun close() {
        entityManagerFactory.close()
    }
}
