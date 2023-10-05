package net.corda.libs.statemanager.impl

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import net.corda.libs.statemanager.api.IntervalFilter
import net.corda.libs.statemanager.api.Metadata
import net.corda.libs.statemanager.api.MetadataFilter
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.libs.statemanager.impl.model.v1.StateEntity
import net.corda.libs.statemanager.impl.repository.StateRepository
import net.corda.orm.utils.transaction
import org.slf4j.LoggerFactory
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory

// TODO-[CORE-17025]: remove Hibernate.
// TODO-[CORE-16323]: check whether the optimistic locking can be improved / merged into single SQL statement.
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
        State(key, value, version, objectMapper.convertToMetadata(metadata), modifiedTime)

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

    override fun updatedBetween(interval: IntervalFilter): Map<String, State> {
        return entityManagerFactory.transaction { em ->
            stateRepository.updatedBetween(em, interval)
        }
            .map { it.fromPersistentEntity() }
            .associateBy { it.key }
    }

    override fun findByMetadataMatchingAll(filters: Collection<MetadataFilter>): Map<String, State> {
        return entityManagerFactory.transaction { em ->
            stateRepository.filterByAll(em, filters)
        }.map {
            it.fromPersistentEntity()
        }.associateBy {
            it.key
        }
    }

    override fun findByMetadataMatchingAny(filters: Collection<MetadataFilter>): Map<String, State> {
        return entityManagerFactory.transaction { em ->
            stateRepository.filterByAny(em, filters)
        }.map {
            it.fromPersistentEntity()
        }.associateBy {
            it.key
        }
    }

    override fun findUpdatedBetweenWithMetadataFilter(
        intervalFilter: IntervalFilter,
        metadataFilter: MetadataFilter
    ): Map<String, State> {
        return entityManagerFactory.transaction { em ->
            stateRepository.filterByUpdatedBetweenAndMetadata(em, intervalFilter, metadataFilter)
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

fun ObjectMapper.convertToMetadata(json: String)  =
    Metadata(this.readValue(json))