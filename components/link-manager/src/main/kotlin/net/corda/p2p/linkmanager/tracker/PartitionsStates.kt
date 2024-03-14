package net.corda.p2p.linkmanager.tracker

import net.corda.libs.statemanager.api.StateManager
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.subscription.listener.PartitionAssignmentListener
import net.corda.p2p.linkmanager.tracker.PartitionState.Companion.stateKey
import net.corda.utilities.time.Clock
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

internal class PartitionsStates(
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val stateManager: StateManager,
    private val config: DeliveryTrackerConfiguration,
    private val clock: Clock,
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(),
) : LifecycleWithDominoTile, PartitionAssignmentListener, DeliveryTrackerConfiguration.ConfigurationChanged {
    private companion object {
        const val NAME = "PartitionsStates"
        const val RETRY_LIMIT_SECONDS_TO_MILLIS = 800.0
        val logger = LoggerFactory.getLogger(this::class.java.name)
    }
    private val task = AtomicReference<Future<*>>()

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

    private fun persist(
        stopRetrying: Instant = clock.instant().plusMillis(
            (config.config.outboundBatchProcessingTimeoutSeconds * RETRY_LIMIT_SECONDS_TO_MILLIS).toLong(),
        ),
        partitionsToPersist: Collection<Int> = partitions.keys,

    ) {
        val group = stateManager.createOperationGroup()
        val updates = partitionsToPersist.mapNotNull {
            partitions[it]
        }.map { stateToPersist ->
            stateToPersist.addToOperationGroup(group)
            stateToPersist.partition
        }

        if (updates.isEmpty()) {
            return
        }
        val failedUpdates = try {
            group.execute()
        } catch (e: RuntimeException) {
            logger.error("Could not update delivery tracker partition states.", e)
            dominoTile.close()
            return
        }

        val reschedule = updates.mapNotNull { partition ->
            val key = stateKey(partition)
            if (failedUpdates.containsKey(key)) {
                logger.info("Could not update delivery tracker for partition: $partition.")
                partition
            } else {
                partitions[partition]?.saved()
                null
            }
        }

        if (reschedule.isNotEmpty()) {
            scheduleRetryUpdate(reschedule, stopRetrying)
        }
    }
    private fun scheduleRetryUpdate(
        partitions: Collection<Int>,
        stopRetrying: Instant,
    ) {
        if (stopRetrying.isAfter(clock.instant())) {
            logger.error("Could not update delivery tracker partition states. Have tried for too long")
            dominoTile.close()
            return
        }

        logger.info("Trying to persist partitions: $partitions again")
        persist(stopRetrying, partitions)
    }

    override fun changed() {
        startTask()
    }

    override fun onPartitionsUnassigned(topicPartitions: List<Pair<String, Int>>) {
        topicPartitions.forEach { (_, partition) ->
            partitions.remove(partition)
        }
    }

    override fun onPartitionsAssigned(topicPartitions: List<Pair<String, Int>>) {
        val partitionsIndices = topicPartitions.map {
            it.second
        }.filter {
            !partitions.contains(it)
        }
        if (partitionsIndices.isEmpty()) {
            return
        }
        val keys = partitionsIndices.map { partitionIndex ->
            stateKey(partitionIndex)
        }
        val states = stateManager.get(keys)
        partitionsIndices.forEach { partitionIndex ->
            val state = states[stateKey(partitionIndex)]
            partitions[partitionIndex] = PartitionState(partitionIndex, state)
        }
    }

    fun get(partition: Int) = partitions[partition]
}
