package net.corda.messaging.db.subscription

import net.corda.lifecycle.LifeCycle
import net.corda.messaging.api.processor.EventLogProcessor
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.PartitionAssignmentListener
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.db.persistence.DBAccessProvider
import net.corda.messaging.db.persistence.DbSchema
import net.corda.messaging.db.persistence.RecordDbEntry
import net.corda.messaging.db.sync.OffsetTrackersManager
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.v5.base.util.seconds
import net.corda.v5.base.util.trace
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.Exception
import java.nio.ByteBuffer
import java.time.Duration
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

@Suppress("LongParameterList")
class DBEventLogSubscription<K: Any, V: Any>(private val subscriptionConfig: SubscriptionConfig,
                                             private val eventLogProcessor: EventLogProcessor<K, V>,
                                             private val partitionAssignmentListener: PartitionAssignmentListener?,
                                             private val avroSchemaRegistry: AvroSchemaRegistry,
                                             private val offsetTrackersManager: OffsetTrackersManager,
                                             private val dbAccessProvider: DBAccessProvider,
                                             private val pollingTimeout: Duration = 1.seconds,
                                             private val batchSize: Int = 100): Subscription<K, V>, LifeCycle {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

    @Volatile
    private var running = false
    private val lock = ReentrantLock()

    private var eventLoopThread: Thread? = null

    override val isRunning: Boolean
        get() = running

    override fun start() {
        lock.withLock {
            if (!running) {
                val maxCommittedOffset =
                    dbAccessProvider.getMaxCommittedOffset(subscriptionConfig.eventTopic, subscriptionConfig.groupName) ?: 0
                eventLoopThread = thread(
                    true,
                    true,
                    null,
                    "DB Subscription processing thread ${subscriptionConfig.groupName}-${subscriptionConfig.eventTopic}",
                    -1
                ) { processingLoop(maxCommittedOffset + 1) }
                partitionAssignmentListener?.onPartitionsAssigned(listOf(subscriptionConfig.eventTopic to DbSchema.FIXED_PARTITION_NO))
                running = true
                log.info("Subscription started for group ${subscriptionConfig.groupName} on topic ${subscriptionConfig.eventTopic}")
            }
        }
    }

    override fun stop() {
        lock.withLock {
            if (running) {
                partitionAssignmentListener?.onPartitionsUnassigned(listOf(subscriptionConfig.eventTopic to DbSchema.FIXED_PARTITION_NO))
                eventLoopThread!!.join(pollingTimeout.toMillis() * 2)
                running = false
                log.info("Subscription stopped for group ${subscriptionConfig.groupName} on topic ${subscriptionConfig.eventTopic}")
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun processingLoop(initialOffset: Long) {
        var nextStartItemOffset = initialOffset
        while (running) {
            try {
                val nextEndItemOffset = nextStartItemOffset + batchSize - 1
                offsetTrackersManager.waitForOffset(subscriptionConfig.eventTopic, nextEndItemOffset, pollingTimeout)

                val maxVisibleOffset = offsetTrackersManager.maxVisibleOffset(subscriptionConfig.eventTopic)
                val dbRecords = dbAccessProvider.readRecords(subscriptionConfig.eventTopic,
                                                                                nextStartItemOffset, maxVisibleOffset, batchSize)

                if (dbRecords.isNotEmpty()) {
                    val records = deserialiseRecordsFromDb(dbRecords)
                    val maxProcessedOffset = records.map { it.offset }.maxOrNull()!!

                    log.trace { "Processing records: $records." }
                    val newRecords = eventLogProcessor.onNext(records)
                    log.trace { "Publishing new records: $newRecords." }

                    publishNewRecordsAndCommitOffset(newRecords, maxProcessedOffset)
                    nextStartItemOffset = maxProcessedOffset + 1
                }
            } catch (e: Exception) {
                log.error("Received error while processing records from topic ${subscriptionConfig.eventTopic}" +
                        "for group ${subscriptionConfig.groupName} at offset $nextStartItemOffset", e)
                //TODO - when the lifecycle framework is ready, change this to notify higher-level components on non-recoverable errors.
            }
        }
    }

    private fun deserialiseRecordsFromDb(dbRecords: List<RecordDbEntry>): List<EventLogRecord<K, V>> {
        return dbRecords.map {
            val deserialisedKey = avroSchemaRegistry.deserialize(ByteBuffer.wrap(it.key), eventLogProcessor.keyClass, null)
            val deserialisedValue = if (it.value == null) {
                null
            } else {
                avroSchemaRegistry.deserialize(ByteBuffer.wrap(it.value), eventLogProcessor.valueClass, null)
            }
            EventLogRecord(it.topic, deserialisedKey, deserialisedValue, it.partition, it.offset)
        }
    }

    private fun publishNewRecordsAndCommitOffset(records: List<Record<*, *>>, offset: Long) {
        val newDbRecords = records.map { toDbRecord(it) }
        if (subscriptionConfig.instanceId == null) {
            dbAccessProvider.writeRecords(newDbRecords) { writtenRecords ->
                writtenRecords.forEach { offsetTrackersManager.offsetReleased(it.topic, it.offset) }
            }
            dbAccessProvider.writeOffset(subscriptionConfig.eventTopic, subscriptionConfig.groupName, offset)
        } else {
            dbAccessProvider.writeOffsetAndRecordsAtomically(subscriptionConfig.eventTopic, subscriptionConfig.groupName,
                offset, newDbRecords) { writtenRecords ->
                writtenRecords.forEach { offsetTrackersManager.offsetReleased(it.topic, it.offset) }
            }
        }
    }

    private fun <K: Any, V: Any> toDbRecord(record: Record<K, V>): RecordDbEntry {
        val offset = offsetTrackersManager.getNextOffset(record.topic)
        val serialisedKey = avroSchemaRegistry.serialize(record.key).array()
        val serialisedValue = if(record.value != null) {
            avroSchemaRegistry.serialize(record.value!!).array()
        } else {
            null
        }
        return RecordDbEntry(record.topic, DbSchema.FIXED_PARTITION_NO, offset, serialisedKey, serialisedValue)
    }

}