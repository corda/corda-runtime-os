package net.corda.p2p.linkmanager.tracker

import net.corda.libs.statemanager.api.StateManager
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.messaging.api.records.EventLogRecord
import net.corda.p2p.linkmanager.tracker.PartitionState.Companion.stateKey
import net.corda.utilities.time.Clock
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

internal class PartitionsStates(
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val stateManager: StateManager,
    private val config: DeliveryTrackerConfiguration,
    private val clock: Clock,
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(),
) : LifecycleWithDominoTile, DeliveryTrackerConfiguration.ConfigurationChanged {
    private companion object {
        const val NAME = "PartitionsStates"
        val logger = LoggerFactory.getLogger(this::class.java.name)
    }
    private val task = AtomicReference<Future<*>>()
    private val numberOfFailedRetries = AtomicInteger(0)

    fun read(records: List<EventLogRecord<String, *>>) {
        val now = clock.instant()
        records.groupBy {
            it.partition
        }.forEach { partition, partitionRecords ->
            partitions[partition]?.read(now, partitionRecords)
        }
    }
    fun sent(records: List<EventLogRecord<String, *>>) {
        records.groupBy {
            it.partition
        }.forEach { (partition, partitionRecords) ->
            partitions[partition]?.sent(partitionRecords)
        }
    }

    private val partitions = ConcurrentHashMap<Int, PartitionState>()

    override val dominoTile = ComplexDominoTile(
        componentName = NAME,
        coordinatorFactory = coordinatorFactory,
        dependentChildren = listOf(stateManager.name, config.dominoTile.coordinatorName),
        onStart = ::onStart,
        onClose = ::onStop,
    )
    private fun onStart() {
        config.lister(this)
        startTask()
    }

    private fun onStop() {
        task.getAndSet(null)?.cancel(false)
    }

    private fun startTask() {
        val statePersistencePeriodMilliseconds = (config.config.statePersistencePeriodSeconds * 1000.0).toLong()
        task.getAndSet(
            executor.scheduleAtFixedRate(
                ::persist,
                statePersistencePeriodMilliseconds,
                statePersistencePeriodMilliseconds,
                TimeUnit.MILLISECONDS,
            ),
        )?.cancel(false)
    }

    private fun persist() {
        if (partitions.isEmpty()) {
            return
        }

        val group = stateManager.createOperationGroup()
        partitions.values.forEach { stateToPersist ->
            stateToPersist.addToOperationGroup(group)
        }
        val failedUpdates = try {
            group.execute().also {
                numberOfFailedRetries.set(0)
            }
        } catch (e: Exception) {
            if (numberOfFailedRetries.incrementAndGet() >= config.config.maxNumberOfPersistenceRetries) {
                logger.error("Could not update delivery tracker partition states.", e)
                dominoTile.setError(e)
            } else {
                logger.warn("Could not update delivery tracker partition states.", e)
            }
            return
        }

        if (failedUpdates.isNotEmpty()) {
            val error = IllegalStateException(
                "Failed to persist the state of partitions ${failedUpdates.keys}." +
                    "Another worker might have the partition.",
            )
            logger.error("Failed to persist the state of the partitions", error)
            dominoTile.setError(error)
            return
        }

        partitions.values.forEach {
            it.saved()
        }
    }

    override fun changed() {
        startTask()
    }

    fun forgetPartitions(partitionsToForget: Set<Int>) {
        partitions.keys.removeAll(partitionsToForget)
    }

    fun loadPartitions(partitionsToLoad: Set<Int>) {
        val partitionsIndices = partitionsToLoad - partitions.keys

        if (partitionsIndices.isEmpty()) {
            return
        }
        val keys = partitionsIndices.map { partitionIndex ->
            stateKey(partitionIndex)
        }
        val states = stateManager.get(keys)
        partitionsIndices.forEach { partitionIndex ->
            val state = states[stateKey(partitionIndex)]
            partitions[partitionIndex] = PartitionState.fromState(partitionIndex, state)
        }
    }

    fun get(partition: Int) = partitions[partition]
}
