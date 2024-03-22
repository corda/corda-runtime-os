package net.corda.libs.statemanager.impl.factory

import net.corda.libs.configuration.SmartConfig
import net.corda.libs.statemanager.api.CompressionType
import net.corda.libs.statemanager.api.IntervalFilter
import net.corda.libs.statemanager.api.MetadataFilter
import net.corda.libs.statemanager.api.Operation
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.libs.statemanager.api.StateManagerFactory
import net.corda.libs.statemanager.api.StateOperationGroup
import net.corda.libs.statemanager.impl.metrics.MetricsRecorder.OperationType.CREATE
import net.corda.libs.statemanager.impl.metrics.MetricsRecorder.OperationType.DELETE
import net.corda.libs.statemanager.impl.metrics.MetricsRecorder.OperationType.FIND
import net.corda.libs.statemanager.impl.metrics.MetricsRecorder.OperationType.GET
import net.corda.libs.statemanager.impl.metrics.MetricsRecorder.OperationType.UPDATE
import net.corda.libs.statemanager.impl.metrics.MetricsRecorderImpl
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.schema.configuration.StateManagerConfig
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * The real state manager implementation requires postgres to run. As a result, it is impossible to plug it into the
 * flow mapper integration tests and have those execute in a non-postgres environment at present.
 *
 * The flow mapper integration tests do not currently need the state manager and so this can be used as a temporary
 * workaround. However, longer term this will not be feasible.
 */
@Component
class TestStateManagerFactoryImpl  @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
) : StateManagerFactory {
    companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private val storage = ConcurrentHashMap<String, State>()

        fun clear() = storage.clear()
    }

    override fun create(config: SmartConfig, stateType: StateManagerConfig.StateType, compressionType: CompressionType): StateManager {
        return  object : StateManager {
            override val name = LifecycleCoordinatorName(
                "TestStateManager",
                UUID.randomUUID().toString()
            )
            private val lifecycleCoordinator = lifecycleCoordinatorFactory.createCoordinator(name) { _, _ ->

            }

            private val metricsRecorder = MetricsRecorderImpl()

            override fun create(states: Collection<State>): Set<String> {
                return metricsRecorder.recordProcessingTime(CREATE) {
                    states.mapNotNull {
                        storage.putIfAbsent(it.key, it)
                    }.map { it.key }.toSet()
                }
            }

            override fun get(keys: Collection<String>): Map<String, State> {
                return metricsRecorder.recordProcessingTime(GET) {
                    keys.mapNotNull { storage[it] }.associateBy { it.key }
                }
            }

            override fun update(states: Collection<State>): Map<String, State> {
                return metricsRecorder.recordProcessingTime(UPDATE) {
                    states.mapNotNull {
                        var output: State? = null
                        storage.compute(it.key) { _, existingState ->
                            if (existingState?.version == it.version) {
                                it.copy(version = it.version + 1)
                            } else {
                                output = it
                                it
                            }
                        }
                        output
                    }.associateBy { it.key }
                }
            }

            override fun delete(states: Collection<State>): Map<String, State> {
                return metricsRecorder.recordProcessingTime(DELETE) {
                    states.mapNotNull {
                        var output: State? = null
                        storage.compute(it.key) { _, existingState ->
                            if (existingState?.version == it.version) {
                                null
                            } else {
                                output = it
                                existingState
                            }
                        }
                        output
                    }.associateBy { it.key }
                }
            }

            override fun createOperationGroup(): StateOperationGroup {
                TODO("Not yet implemented")
            }

            override fun updatedBetween(interval: IntervalFilter): Map<String, State> {
                TODO("Not yet implemented")
            }

            override fun findByMetadataMatchingAll(filters: Collection<MetadataFilter>): Map<String, State> {
                TODO("Not yet implemented")
            }

            override fun findByMetadataMatchingAny(filters: Collection<MetadataFilter>): Map<String, State> {
                TODO("Not yet implemented")
            }

            // Only supporting equals for now.
            override fun findUpdatedBetweenWithMetadataFilter(
                intervalFilter: IntervalFilter,
                metadataFilter: MetadataFilter
            ): Map<String, State> {
                return metricsRecorder.recordProcessingTime(FIND) {
                    storage.filter { (_, state) ->
                        state.modifiedTime >= intervalFilter.start && state.modifiedTime <= intervalFilter.finish
                    }.filter {
                        matchesAll(it.value, listOf(metadataFilter))
                    }
                }
            }

            override fun findUpdatedBetweenWithMetadataMatchingAll(
                intervalFilter: IntervalFilter,
                metadataFilters: Collection<MetadataFilter>
            ): Map<String, State> {
                return metricsRecorder.recordProcessingTime(FIND) {
                    storage.filter { (_, state) ->
                        state.modifiedTime >= intervalFilter.start && state.modifiedTime <= intervalFilter.finish
                    }.filter {
                        matchesAll(it.value, metadataFilters)
                    }
                }
            }

            override fun findUpdatedBetweenWithMetadataMatchingAny(
                intervalFilter: IntervalFilter,
                metadataFilters: Collection<MetadataFilter>
            ): Map<String, State> {
                return metricsRecorder.recordProcessingTime(FIND) {
                    storage.filter { (_, state) ->
                        state.modifiedTime >= intervalFilter.start && state.modifiedTime <= intervalFilter.finish
                    }.filter {
                        matchesAny(it.value, metadataFilters)
                    }
                }
            }

            @Suppress("UNCHECKED_CAST")
            private fun matchesAny(state: State, filters: Collection<MetadataFilter>) =
                filters.any {
                    if (state.metadata.containsKey(it.key)) {
                        val source = state.metadata[it.key] as Comparable<Any>

                        when (it.operation) {
                            Operation.Equals -> source == it.value
                            Operation.NotEquals -> source != it.value
                            Operation.LesserThan -> source < it.value
                            Operation.GreaterThan -> source > it.value
                        }
                    } else {
                        false
                    }
                }

            @Suppress("UNCHECKED_CAST")
            private fun matchesAll(state: State, filters: Collection<MetadataFilter>) =
                filters.all {
                    if (state.metadata.containsKey(it.key)) {
                        val source = state.metadata[it.key] as Comparable<Any>

                        when (it.operation) {
                            Operation.Equals -> source == it.value
                            Operation.NotEquals -> source != it.value
                            Operation.LesserThan -> source < it.value
                            Operation.GreaterThan -> source > it.value
                        }
                    } else {
                        false
                    }
                }

            override val isRunning: Boolean
                get() {
                    log.info("$name running = ${lifecycleCoordinator.isRunning}")
                    return lifecycleCoordinator.isRunning
                }

            override fun start() {
                log.info("$name started")
                lifecycleCoordinator.start()
            }

            override fun stop() {
                log.info("$name closed")
                lifecycleCoordinator.close()
            }
        }
    }
}
