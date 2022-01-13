package net.corda.messaging.db.subscription

import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.messaging.api.processor.EventLogProcessor
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.PartitionAssignmentListener
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.db.partition.PartitionAllocationListener
import net.corda.messaging.db.partition.PartitionAllocator
import net.corda.messaging.db.partition.PartitionAssignor
import net.corda.messaging.db.persistence.DBAccessProvider
import net.corda.messaging.db.persistence.OffsetsAlreadyCommittedException
import net.corda.messaging.db.persistence.RecordDbEntry
import net.corda.messaging.db.sync.OffsetTrackersManager
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.seconds
import net.corda.v5.base.util.trace
import org.slf4j.Logger
import java.nio.ByteBuffer
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

@Suppress("LongParameterList")
class DBEventLogSubscription<K : Any, V : Any>(
    private val subscriptionConfig: SubscriptionConfig,
    private val eventLogProcessor: EventLogProcessor<K, V>,
    private val partitionAssignmentListener: PartitionAssignmentListener?,
    private val avroSchemaRegistry: AvroSchemaRegistry,
    private val offsetTrackersManager: OffsetTrackersManager,
    private val partitionAllocator: PartitionAllocator,
    private val partitionAssignor: PartitionAssignor,
    private val dbAccessProvider: DBAccessProvider,
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    private val pollingTimeout: Duration = 1.seconds,
    private val batchSize: Int = 100
) : Subscription<K, V> {

    companion object {
        private val log: Logger = contextLogger()
    }

    @Volatile
    private var running = false
    private val startStopLock = ReentrantLock()
    private val lifecycleCoordinator = lifecycleCoordinatorFactory.createCoordinator(
        LifecycleCoordinatorName(
            "${subscriptionConfig.groupName}-DurableSubscription-${subscriptionConfig.eventTopic}",
            subscriptionConfig.instanceId.toString()
        )
    ) { _, _ -> }

    private var eventLoopThread: Thread? = null

    override val isRunning: Boolean
        get() = running

    private val maxCommittedOffsetsPerAssignedPartition: ConcurrentMap<Int, Long> = ConcurrentHashMap()
    private lateinit var partitionsPerTopic: Map<String, Int>

    private val fetchWindowCalculator = FetchWindowCalculator(offsetTrackersManager)

    override fun start() {
        startStopLock.withLock {
            if (!running) {
                partitionsPerTopic = dbAccessProvider.getTopics()
                val partitionAllocationListener = object : PartitionAllocationListener {
                    override fun onPartitionsAssigned(topic: String, partitions: Set<Int>) {
                        val maxCommittedOffsetPerPartition =
                            dbAccessProvider.getMaxCommittedOffset(
                                subscriptionConfig.eventTopic,
                                subscriptionConfig.groupName,
                                partitions
                            )
                        maxCommittedOffsetPerPartition.forEach { (partition, offset) ->
                            maxCommittedOffsetsPerAssignedPartition[partition] = offset ?: -1
                        }
                        partitionAssignmentListener?.onPartitionsAssigned(partitions.map { topic to it })
                    }

                    override fun onPartitionsUnassigned(topic: String, partitions: Set<Int>) {
                        partitions.forEach { maxCommittedOffsetsPerAssignedPartition.remove(it) }
                        partitionAssignmentListener?.onPartitionsUnassigned(partitions.map { topic to it })
                    }
                }
                partitionAllocator.register(subscriptionConfig.eventTopic, partitionAllocationListener)
                running = true
                eventLoopThread = thread(
                    true,
                    true,
                    null,
                    "DB Subscription processing thread ${subscriptionConfig.groupName}-${subscriptionConfig.eventTopic}",
                    -1
                ) {
                    lifecycleCoordinator.start()
                    lifecycleCoordinator.updateStatus(LifecycleStatus.UP)
                    processingLoop()
                }
                log.info("Subscription started for group ${subscriptionConfig.groupName} on topic ${subscriptionConfig.eventTopic}")
            }
            lifecycleCoordinator.updateStatus(LifecycleStatus.DOWN)
        }
    }

    override fun stop() {
        startStopLock.withLock {
            if (running) {
                stopConsumer()
                lifecycleCoordinator.stop()
                log.info("Subscription stopped for group ${subscriptionConfig.groupName} on topic ${subscriptionConfig.eventTopic}")
            }
        }
    }

    override fun close() {
        startStopLock.withLock {
            if (running) {
                stopConsumer()
                lifecycleCoordinator.close()
                log.info("Subscription closed for group ${subscriptionConfig.groupName} on topic ${subscriptionConfig.eventTopic}")
            }
        }
    }

    private fun stopConsumer() {
        eventLoopThread!!.join(pollingTimeout.toMillis() * 2)
        running = false
    }

    private fun processingLoop() {
        while (running) {
            val currentlyAssignedPartitionsAndCommittedOffsets = HashMap(maxCommittedOffsetsPerAssignedPartition)
            if (currentlyAssignedPartitionsAndCommittedOffsets.isNotEmpty()) {
                processNextBatchOfRecords(currentlyAssignedPartitionsAndCommittedOffsets)
            } else {
                Thread.sleep(pollingTimeout.toMillis())
            }
        }
    }

    private fun processNextBatchOfRecords(partitionsAndCommittedOffsets: Map<Int, Long>) {
        try {
            val offsetWindowPerPartition = batchSize / partitionsAndCommittedOffsets.keys.size
            val offsetsToWaitFor = partitionsAndCommittedOffsets.map { (partition, maxCommittedOffset) ->
                partition to (maxCommittedOffset + offsetWindowPerPartition)
            }.toMap()
            offsetTrackersManager.waitForOffsets(subscriptionConfig.eventTopic, offsetsToWaitFor, pollingTimeout)

            val fetchWindows =
                fetchWindowCalculator.calculateWindows(
                    subscriptionConfig.eventTopic,
                    batchSize,
                    partitionsAndCommittedOffsets
                )
            val dbRecords = dbAccessProvider.readRecords(subscriptionConfig.eventTopic, fetchWindows)

            if (dbRecords.isNotEmpty()) {
                val records = deserialiseRecordsFromDb(dbRecords)
                val maxProcessedOffsets = records
                    .groupBy { it.partition }
                    .mapValues { it.value.map { it.offset }.maxOrNull()!! }

                log.trace { "Processing records: $records." }
                val newRecords = eventLogProcessor.onNext(records)
                log.trace { "Publishing new records: $newRecords." }

                publishNewRecordsAndCommitOffset(newRecords, maxProcessedOffsets)
                maxProcessedOffsets.forEach { (partition, offset) ->
                    maxCommittedOffsetsPerAssignedPartition.computeIfPresent(partition) { _, _ -> offset }
                }
            }
        } catch (e: Exception) {
            val message = "Received error while processing records from topic ${subscriptionConfig.eventTopic} " +
                    "for group ${subscriptionConfig.groupName} at offsets $partitionsAndCommittedOffsets"
            when (e) {
                is OffsetsAlreadyCommittedException -> {
                    log.warn(message, e)
                    // another subscription must have committed new offsets (before losing the partition),
                    // so retrieve committed offsets again and retry.
                    val maxCommittedOffsetPerPartition = dbAccessProvider.getMaxCommittedOffset(
                        subscriptionConfig.eventTopic,
                        subscriptionConfig.groupName, partitionsAndCommittedOffsets.keys
                    )
                    maxCommittedOffsetPerPartition.forEach { (partition, offset) ->
                        maxCommittedOffsetsPerAssignedPartition.computeIfPresent(partition) { _, _ -> offset ?: -1 }
                    }
                }
                else -> {
                    log.error(message, e)
                    lifecycleCoordinator.updateStatus(LifecycleStatus.ERROR, message)
                }
            }
        }
    }

    private fun deserialiseRecordsFromDb(dbRecords: List<RecordDbEntry>): List<EventLogRecord<K, V>> {
        return dbRecords.map {
            val deserialisedKey =
                avroSchemaRegistry.deserialize(ByteBuffer.wrap(it.key), eventLogProcessor.keyClass, null)
            val deserialisedValue = if (it.value == null) {
                null
            } else {
                avroSchemaRegistry.deserialize(ByteBuffer.wrap(it.value), eventLogProcessor.valueClass, null)
            }
            EventLogRecord(it.topic, deserialisedKey, deserialisedValue, it.partition, it.offset)
        }
    }

    private fun publishNewRecordsAndCommitOffset(records: List<Record<*, *>>, offsetsPerPartition: Map<Int, Long>) {
        val newDbRecords = records.map { toDbRecord(it) }
        if (subscriptionConfig.instanceId == null) {
            dbAccessProvider.writeRecords(newDbRecords) { writtenRecords, _ ->
                writtenRecords.forEach { offsetTrackersManager.offsetReleased(it.topic, it.partition, it.offset) }
            }
            dbAccessProvider.writeOffsets(
                subscriptionConfig.eventTopic,
                subscriptionConfig.groupName,
                offsetsPerPartition
            )
        } else {
            dbAccessProvider.writeOffsetsAndRecordsAtomically(
                subscriptionConfig.eventTopic, subscriptionConfig.groupName,
                offsetsPerPartition, newDbRecords
            ) { writtenRecords, _ ->
                writtenRecords.forEach { offsetTrackersManager.offsetReleased(it.topic, it.partition, it.offset) }
            }
        }
    }

    private fun <K : Any, V : Any> toDbRecord(record: Record<K, V>): RecordDbEntry {
        val serialisedKey = avroSchemaRegistry.serialize(record.key).array()
        val serialisedValue = if (record.value != null) {
            avroSchemaRegistry.serialize(record.value!!).array()
        } else {
            null
        }
        val partition = partitionAssignor.assign(serialisedKey, partitionsPerTopic[record.topic]!!)
        val offset = offsetTrackersManager.getNextOffset(record.topic, partition)

        return RecordDbEntry(record.topic, partition, offset, serialisedKey, serialisedValue)
    }

    override val subscriptionName: LifecycleCoordinatorName
        get() = LifecycleCoordinatorName("DBEventLogSubscription")

}