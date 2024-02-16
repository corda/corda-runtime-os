package net.corda.libs.statemanager.impl

import net.corda.db.core.CloseableDataSource
import net.corda.db.core.utils.transaction
import net.corda.libs.statemanager.api.IntervalFilter
import net.corda.libs.statemanager.api.MetadataFilter
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.libs.statemanager.api.StateOperationGroup
import net.corda.libs.statemanager.impl.lifecycle.CheckConnectionEventHandler
import net.corda.libs.statemanager.impl.metrics.MetricsRecorder
import net.corda.libs.statemanager.impl.metrics.MetricsRecorder.OperationType.CREATE
import net.corda.libs.statemanager.impl.metrics.MetricsRecorder.OperationType.DELETE
import net.corda.libs.statemanager.impl.metrics.MetricsRecorder.OperationType.FIND
import net.corda.libs.statemanager.impl.metrics.MetricsRecorder.OperationType.GET
import net.corda.libs.statemanager.impl.metrics.MetricsRecorder.OperationType.UPDATE
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
    private val metricsRecorder: MetricsRecorder,
) : StateManager {
    override val name = LifecycleCoordinatorName(
        "StateManager",
        UUID.randomUUID().toString()
    )
    private val eventHandler = CheckConnectionEventHandler(name) { dataSource.connection.close() }
    private val lifecycleCoordinator = lifecycleCoordinatorFactory.createCoordinator(name, eventHandler)

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    /**
     * Internal method to retrieve states by key without recording any metrics.
     */
    private fun getByKey(keys: Collection<String>): Map<String, State> {
        if (keys.isEmpty()) return emptyMap()

        return dataSource.connection.use { connection ->
            stateRepository.get(connection, keys)
        }.associateBy {
            it.key
        }
    }

    private fun getFailedUpdates(failedUpdates: List<String>): Map<String, State?> {
        val failedByOptimisticLocking = getByKey(failedUpdates)
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

    override fun create(states: Collection<State>): Set<String> {
        if (states.isEmpty()) return emptySet()
        val duplicateStatesKeys = states.groupBy {
            it.key
        }.filter {
            it.value.size > 1
        }.keys
        if (duplicateStatesKeys.isNotEmpty()) {
            throw IllegalArgumentException(
                "Creating multiple states with the same key is not supported," +
                        " duplicated keys found: $duplicateStatesKeys"
            )
        }

        return metricsRecorder.recordProcessingTime(CREATE) {
            val successfulKeys = dataSource.connection.transaction { connection ->
                stateRepository.create(connection, states)
            }

            states.map { it.key }.toSet() - successfulKeys.toSet()
        }
    }

    override fun get(keys: Collection<String>): Map<String, State> {
        if (keys.isEmpty()) return emptyMap()

        return metricsRecorder.recordProcessingTime(GET) {
            getByKey(keys)
        }
    }

    override fun update(states: Collection<State>): Map<String, State?> {
        if (states.isEmpty()) return emptyMap()

        return metricsRecorder.recordProcessingTime(UPDATE) {
            try {
                val (_, failedUpdates) = dataSource.connection.transaction { conn ->
                    stateRepository.update(conn, states)
                }

                if (failedUpdates.isEmpty()) {
                    emptyMap()
                } else {
                    getFailedUpdates(failedUpdates)
                }
            } catch (e: Exception) {
                logger.warn("Failed to updated batch of states - ${states.joinToString { it.key }}", e)
                throw e
            }
        }
    }

    override fun delete(states: List<String>): Collection<String> {
        if (states.isEmpty()) return emptyList()

        return metricsRecorder.recordProcessingTime(MetricsRecorder.OperationType.DELETE_NO_LOCKING) {
            try {
                val failedDeletes = dataSource.connection.transaction { connection ->
                    stateRepository.delete(connection, states)
                }

                if (failedDeletes.isEmpty()) {
                    emptyList()
                } else {
                    logger.warn(
                        "Failed to delete States without optimistic locking" +
                                " ${failedDeletes.joinToString()}"
                    )
                    failedDeletes
                }
            } catch (e: Exception) {
                logger.warn("Failed to delete batch of states without locking - ${states.joinToString()}", e)
                throw e
            }
        }
    }

    override fun delete(states: Collection<State>): Map<String, State> {
        if (states.isEmpty()) return emptyMap()

        return metricsRecorder.recordProcessingTime(DELETE) {
            try {
                val failedDeletes = dataSource.connection.transaction { connection ->
                    stateRepository.delete(connection, states)
                }

                if (failedDeletes.isEmpty()) {
                    emptyMap()
                } else {
                    getByKey(failedDeletes).also {
                        if (it.isNotEmpty()) {
                            logger.warn(
                                "Optimistic locking check failed while deleting States" +
                                        " ${failedDeletes.joinToString()}"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                logger.warn("Failed to delete batch of states - ${states.joinToString { it.key }}", e)
                throw e
            }
        }
    }

    override fun createOperationGroup(): StateOperationGroup {
        return StateOperationGroupImpl(dataSource, stateRepository)
    }

    override fun updatedBetween(interval: IntervalFilter): Map<String, State> {
        return metricsRecorder.recordProcessingTime(FIND) {
            dataSource.connection.use { connection ->
                stateRepository.updatedBetween(connection, interval)
            }.associateBy {
                it.key
            }
        }
    }

    override fun findByMetadataMatchingAll(filters: Collection<MetadataFilter>): Map<String, State> {
        if (filters.isEmpty()) return emptyMap()

        return metricsRecorder.recordProcessingTime(FIND) {
            dataSource.connection.use { connection ->
                stateRepository.filterByAll(connection, filters)
            }.associateBy {
                it.key
            }
        }
    }

    override fun findByMetadataMatchingAny(filters: Collection<MetadataFilter>): Map<String, State> {
        if (filters.isEmpty()) return emptyMap()

        return metricsRecorder.recordProcessingTime(FIND) {
            dataSource.connection.use { connection ->
                stateRepository.filterByAny(connection, filters)
            }.associateBy {
                it.key
            }
        }
    }

    override fun findUpdatedBetweenWithMetadataMatchingAll(
        intervalFilter: IntervalFilter,
        metadataFilters: Collection<MetadataFilter>
    ): Map<String, State> {
        return metricsRecorder.recordProcessingTime(FIND) {
            dataSource.connection.use { connection ->
                stateRepository.filterByUpdatedBetweenWithMetadataMatchingAll(
                    connection,
                    intervalFilter,
                    metadataFilters
                )
            }.associateBy {
                it.key
            }
        }
    }

    override fun findUpdatedBetweenWithMetadataMatchingAny(
        intervalFilter: IntervalFilter,
        metadataFilters: Collection<MetadataFilter>
    ): Map<String, State> {
        return metricsRecorder.recordProcessingTime(FIND) {
            dataSource.connection.use { connection ->
                stateRepository.filterByUpdatedBetweenWithMetadataMatchingAny(
                    connection,
                    intervalFilter,
                    metadataFilters
                )
            }.associateBy {
                it.key
            }
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
