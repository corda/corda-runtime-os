package net.corda.p2p.linkmanager.tracker

import net.corda.data.p2p.app.AppMessage
import net.corda.libs.statemanager.api.StateManager
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.messaging.api.records.EventLogRecord
import net.corda.p2p.linkmanager.delivery.ReplayScheduler
import net.corda.p2p.linkmanager.sessions.SessionManager
import net.corda.p2p.linkmanager.tracker.PartitionState.Companion.stateKey
import net.corda.utilities.time.Clock
import net.corda.virtualnode.toCorda
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@Suppress("LongParameterList")
internal class PartitionsStates(
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val stateManager: StateManager,
    private val config: DeliveryTrackerConfiguration,
    private val clock: Clock,
    private val replayScheduler: ReplayScheduler<SessionManager.Counterparties, String>,
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(),
) : LifecycleWithDominoTile, DeliveryTrackerConfiguration.ConfigurationChanged {
    private companion object {
        const val NAME = "PartitionsStates"
        val logger = LoggerFactory.getLogger(this::class.java.name)
    }
    private val task = AtomicReference<Future<*>>()
    private val numberOfFailedRetries = AtomicInteger(0)

    fun getEventToProcess(
        records: Collection<EventLogRecord<String, AppMessage>>,
    ): List<EventLogRecord<String, AppMessage>> {
        return records.groupBy {
            it.partition
        }.flatMap { (partition, records) ->
            val processRecordsFromOffset = partitions[partition]?.processRecordsFromOffset ?: -1
            records.filter {
                it.offset > processRecordsFromOffset
            }
        }
    }

    fun read(records: Collection<MessageRecord>) {
        val now = clock.instant()
        records.groupBy {
            it.partition
        }.forEach { (partition, partitionRecords) ->
            partitions[partition]?.read(now, partitionRecords)?.forEach { messageRecord ->
                val counterparties = SessionManager.Counterparties(
                    ourId = messageRecord.message.header.source.toCorda(),
                    counterpartyId = messageRecord.message.header.destination.toCorda(),
                )
                replayScheduler.addForReplay(
                    originalAttemptTimestamp = now.toEpochMilli(),
                    messageId = messageRecord.message.header.messageId,
                    message = messageRecord.message.header.messageId,
                    counterparties = counterparties,
                )
            }
        }
    }

    fun forget(messageRecord: MessageRecord) {
        val info = partitions[messageRecord.partition]
        info?.forget(messageRecord.message)
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
        logger.info("YYY In persist - thread ID - ${Thread.currentThread().id} partitions size: ${partitions.size}...")
        partitions.values.forEach { stateToPersist ->
            stateToPersist.trim()
            logger.info("YYY \t adding partition to group...")
            stateToPersist.addToOperationGroup(group)
        }
        val failedUpdates = try {
            logger.info("YYY Going to execute...")
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
            logger.info("YYY saved...")
            it.saved()
        }
    }

    override fun changed() {
        startTask()
    }

    fun forgetPartitions(partitionsToForget: Set<Int>) {
        partitions.keys.removeAll(partitionsToForget)
    }

    fun loadPartitions(partitionsToLoad: Set<Int>): Map<Int, PartitionState> {
        val partitionsIndices = partitionsToLoad - partitions.keys
        partitions.putAll(
            loadPartitionsFromStateManager(partitionsIndices).also { loadedPartitions ->
                loadedPartitions.values.flatMap {
                    it.counterpartiesToMessages()
                }.forEach { (counterparties, messages) ->
                    messages.forEach {
                        replayScheduler.addForReplay(
                            it.timeStamp.toEpochMilli(),
                            it.messageId,
                            it.messageId,
                            counterparties,
                        )
                    }
                }
            },
        )
        return partitionsToLoad.associateWith {
            partitions[it] ?: PartitionState(it)
        }
    }

    private fun loadPartitionsFromStateManager(partitionsIndices: Set<Int>): Map<Int, PartitionState> {
        if (partitionsIndices.isEmpty()) {
            return emptyMap()
        }
        val keys = partitionsIndices.map { partitionIndex ->
            stateKey(partitionIndex)
        }
        val states = stateManager.get(keys)
        return partitionsIndices.associateWith { partitionIndex ->
            val state = states[stateKey(partitionIndex)]
            PartitionState.fromState(partitionIndex, state)
        }
    }

    fun offsetsToReadFromChanged(partitionsToLastPersistedOffset: Collection<Pair<Int, Long>>) {
        partitionsToLastPersistedOffset.forEach { (partition, offset) ->
            partitions[partition]?.readRecordsFromOffset = offset
        }
    }

    fun handled(events: Collection<EventLogRecord<String, AppMessage>>) {
        events.groupBy {
            it.partition
        }.forEach { (partition, records) ->
            val offset = records.maxOf { it.offset }
            partitions[partition]?.processRecordsFromOffset = offset
        }
    }
}
