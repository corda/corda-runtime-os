package net.corda.libs.statemanager.impl

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.libs.statemanager.api.IntervalFilter
import net.corda.libs.statemanager.api.MetadataFilter
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.libs.statemanager.impl.model.v1.StateEntity
import net.corda.libs.statemanager.impl.repository.StateRepository
import net.corda.orm.utils.transaction
import org.slf4j.LoggerFactory
import javax.persistence.EntityManagerFactory

// TODO-[CORE-17025]: remove Hibernate.
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
        try {
            entityManagerFactory.transaction { em ->
                val failedUpdates = stateRepository.update(em, states.map { it.toPersistentEntity() })

                return if (failedUpdates.isEmpty()) {
                    emptyMap()
                } else {
                    logger.warn("Optimistic locking check failed while updating States ${failedUpdates.joinToString()}")
                    get(failedUpdates)
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to updated batch of states - ${states.joinToString { it.key }}", e)
            throw e
        }
    }

    override fun delete(states: Collection<State>): Map<String, State> {
        try {
            entityManagerFactory.transaction { em ->
                val failedDeletes = stateRepository.delete(em, states.map { it.toPersistentEntity() })

                return if (failedDeletes.isEmpty()) {
                    emptyMap()
                } else {
                    logger.warn("Optimistic locking check failed while deleting States ${failedDeletes.joinToString()}")
                    get(failedDeletes)
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to delete batch of states - ${states.joinToString { it.key }}", e)
            throw e
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
