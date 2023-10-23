package net.corda.libs.statemanager.impl

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.db.core.CloseableDataSource
import net.corda.db.core.utils.transaction
import net.corda.libs.statemanager.api.IntervalFilter
import net.corda.libs.statemanager.api.MetadataFilter
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.libs.statemanager.impl.model.v1.StateEntity
import net.corda.libs.statemanager.impl.repository.StateRepository
import net.corda.libs.statemanager.impl.repository.impl.StateManagerBatchingException
import net.corda.orm.utils.transaction
import org.slf4j.LoggerFactory
import javax.persistence.EntityManagerFactory

// TODO-[CORE-17025]: remove Hibernate.
class StateManagerImpl(
    private val stateRepository: StateRepository,
    private val entityManagerFactory: EntityManagerFactory,
    private val dataSource: CloseableDataSource,
) : StateManager {

    private companion object {
        private val objectMapper = ObjectMapper()
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private fun State.toPersistentEntity(): StateEntity =
        StateEntity(key, value, objectMapper.writeValueAsString(metadata), version, modifiedTime)

    private fun StateEntity.fromPersistentEntity() =
        State(key, value, version, objectMapper.convertToMetadata(metadata), modifiedTime)

    override fun create(states: Collection<State>): Collection<String> {
        val stateEntities = states.map {
            it.toPersistentEntity()
        }
        return createStatesWithRetryOnBatchError(stateEntities)
    }

    /**
     * Try create states in a batch in a single transaction.
     *
     * If an error happens that throws a [StateManagerBatchingException], attempt retry of each failed state
     * individually.
     *
     * Return collection of failed keys.
     *
     * @param stateEntities a list of states to create
     * @return a collection of keys of states that failed to persist
     */
    private fun createStatesWithRetryOnBatchError(stateEntities: List<StateEntity>): Collection<String> {
        return try {
            dataSource.connection.transaction { conn ->
                stateRepository.create(conn, stateEntities)
            }
        } catch (e: StateManagerBatchingException) {
            logger.info("Error during batch state creation. Retrying creation of ${e.failedStates.size} states without batching.", e)
            return e.failedStates.filterNot {
                dataSource.connection.transaction { conn ->
                    stateRepository.create(conn, it)
                }
            }.map { it.key }
        }
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
            val failedUpdates = dataSource.connection.transaction { conn ->
                stateRepository.update(conn, states.map { it.toPersistentEntity() })
            }

            return if (failedUpdates.isEmpty()) {
                emptyMap()
            } else {
                logger.warn("Optimistic locking check failed while updating States ${failedUpdates.joinToString()}")
                get(failedUpdates)
            }
        } catch (e: Exception) {
            logger.warn("Failed to updated batch of states - ${states.joinToString { it.key }}", e)
            throw e
        }
    }

    override fun delete(states: Collection<State>): Map<String, State> {
        try {
            val failedDeletes = dataSource.connection.transaction { conn ->
                stateRepository.delete(conn, states.map { it.toPersistentEntity() })
            }

            return if (failedDeletes.isEmpty()) {
                emptyMap()
            } else {
                logger.warn("Optimistic locking check failed while deleting States ${failedDeletes.joinToString()}")
                get(failedDeletes)
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
