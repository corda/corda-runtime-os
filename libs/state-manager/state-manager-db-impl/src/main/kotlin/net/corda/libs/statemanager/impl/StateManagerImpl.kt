package net.corda.libs.statemanager.impl

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.db.core.CloseableDataSource
import net.corda.db.core.utils.transaction
import net.corda.libs.statemanager.api.IntervalFilter
import net.corda.libs.statemanager.api.MetadataFilter
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.libs.statemanager.impl.lifecycle.CheckConnectionEventHandler
import net.corda.libs.statemanager.impl.model.v1.StateEntity
import net.corda.libs.statemanager.impl.repository.StateRepository
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import org.slf4j.LoggerFactory
import java.util.UUID

@Suppress("TooManyFunctions")
class StateManagerImpl(
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    private val dataSource: CloseableDataSource,
    private val stateRepository: StateRepository,
) : StateManager {
    override val name = LifecycleCoordinatorName(
        "StateManager",
        UUID.randomUUID().toString()
    )
    private val eventHandler = CheckConnectionEventHandler(name) { dataSource.connection.close() }
    private val lifecycleCoordinator = lifecycleCoordinatorFactory.createCoordinator(name, eventHandler)

    private companion object {
        private val objectMapper = ObjectMapper()
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private fun State.toPersistentEntity(): StateEntity =
        StateEntity(key, value, objectMapper.writeValueAsString(metadata), version, modifiedTime)

    private fun StateEntity.fromPersistentEntity() =
        State(key, value, version, objectMapper.convertToMetadata(metadata), modifiedTime)

    override fun create(states: Collection<State>): Set<String> {
        if (states.isEmpty()) return emptySet()
        val successfulKeys = dataSource.connection.transaction { connection ->
            stateRepository.create(connection, states.map { it.toPersistentEntity() })
        }
        return getFailedCreates(states, successfulKeys.toSet())
    }

    override fun createOrUpdate(states: Collection<State>): Map<String, State> {
        if (states.isEmpty()) return emptyMap()
        return dataSource.connection.transaction { connection ->
            stateRepository.createOrUpdate(connection, states.map { it.toPersistentEntity() })
        }.map {
            it.fromPersistentEntity()
        }.associateBy {
            it.key
        }
    }

    private fun getFailedCreates(states: Collection<State>, successfulKeys: Set<String>) =
        states.map { it.key }.toSet() - successfulKeys

    override fun get(keys: Collection<String>): Map<String, State> {
        return if (keys.isEmpty()) {
            emptyMap()
        } else {
            dataSource.connection.transaction { connection ->
                stateRepository.get(connection, keys)
            }.map {
                it.fromPersistentEntity()
            }.associateBy {
                it.key
            }
        }
    }

    override fun update(states: Collection<State>): Map<String, State?> {
        if (states.isEmpty()) return emptyMap()

        try {
            val (_, failedUpdates) = dataSource.connection.transaction { conn ->
                stateRepository.update(conn, states.map { it.toPersistentEntity() })
            }

            return getFailedUpdates(failedUpdates)
        } catch (e: Exception) {
            logger.warn("Failed to updated batch of states - ${states.joinToString { it.key }}", e)
            throw e
        }
    }

    private fun getFailedUpdates(failedUpdates: List<String>): Map<String, State?> {
        if (failedUpdates.isEmpty()) {
            return emptyMap()
        }

        val failedByOptimisticLocking = get(failedUpdates)
        val failedByNotExisting = (failedUpdates - failedByOptimisticLocking.keys)

        var warning = ""
        if (failedByOptimisticLocking.isNotEmpty()) {
            warning += "Optimistic locking prevented updates to the following States: " +
                failedByOptimisticLocking.keys.joinToString(postfix = ". ")
        }

        if (failedByNotExisting.isNotEmpty()) {
            warning += "Failed to update the following States because they did not exist or were already deleted: " +
                failedByNotExisting.joinToString(postfix = ".")
        }

        logger.warn(warning)

        return failedByOptimisticLocking + (failedByNotExisting.associateWith { null })
    }

    override fun delete(states: Collection<State>): Map<String, State> {
        if (states.isEmpty()) return emptyMap()

        try {
            val failedDeletes = dataSource.connection.transaction { connection ->
                stateRepository.delete(connection, states.map { it.toPersistentEntity() })
            }

            return getFailedDeletes(failedDeletes)
        } catch (e: Exception) {
            logger.warn("Failed to delete batch of states - ${states.joinToString { it.key }}", e)
            throw e
        }
    }

    private fun getFailedDeletes(failedDeletes: Collection<String>) = if (failedDeletes.isEmpty()) {
        emptyMap()
    } else {
        get(failedDeletes).also {
            if (it.isNotEmpty()) {
                logger.warn(
                    "Optimistic locking check failed while deleting States" +
                        " ${failedDeletes.joinToString()}"
                )
            }
        }
    }

    override fun updatedBetween(interval: IntervalFilter): Map<String, State> {
        return dataSource.connection.transaction { connection ->
            stateRepository.updatedBetween(connection, interval)
        }
            .map { it.fromPersistentEntity() }
            .associateBy { it.key }
    }

    override fun findByMetadataMatchingAll(filters: Collection<MetadataFilter>): Map<String, State> {
        return if (filters.isEmpty()) {
            emptyMap()
        } else {
            dataSource.connection.transaction { connection ->
                stateRepository.filterByAll(connection, filters)
            }.map {
                it.fromPersistentEntity()
            }.associateBy {
                it.key
            }
        }
    }

    override fun findByMetadataMatchingAny(filters: Collection<MetadataFilter>): Map<String, State> {
        return if (filters.isEmpty()) {
            emptyMap()
        } else {
            dataSource.connection.transaction { connection ->
                stateRepository.filterByAny(connection, filters)
            }.map {
                it.fromPersistentEntity()
            }.associateBy {
                it.key
            }
        }
    }

    override fun findUpdatedBetweenWithMetadataMatchingAll(
        intervalFilter: IntervalFilter,
        metadataFilters: Collection<MetadataFilter>
    ): Map<String, State> {
        return dataSource.connection.transaction { connection ->
            stateRepository.filterByUpdatedBetweenWithMetadataMatchingAll(connection, intervalFilter, metadataFilters)
        }.map {
            it.fromPersistentEntity()
        }.associateBy {
            it.key
        }
    }

    override fun findUpdatedBetweenWithMetadataMatchingAny(
        intervalFilter: IntervalFilter,
        metadataFilters: Collection<MetadataFilter>
    ): Map<String, State> {
        return dataSource.connection.transaction { connection ->
            stateRepository.filterByUpdatedBetweenWithMetadataMatchingAny(connection, intervalFilter, metadataFilters)
        }.map {
            it.fromPersistentEntity()
        }.associateBy {
            it.key
        }
    }

    override val isRunning: Boolean
        get() = lifecycleCoordinator.isRunning

    override fun start() {
        lifecycleCoordinator.start()
    }

    override fun stop() {
        lifecycleCoordinator.close()
    }
}
