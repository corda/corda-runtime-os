package net.corda.messaging.emulation.topic.model

import net.corda.v5.base.util.contextLogger
import org.slf4j.Logger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock

internal class ConsumerGroup(
    private val groupName: String,
    private val topicName: String,
    private val partitions: Collection<Partition>,
    private val lock: ReadWriteLock = ReentrantReadWriteLock(),
) {
    private val consumers = ConcurrentHashMap<Consumer, Collection<Partition>>()
    private val commitments = ConcurrentHashMap<Partition, Long>()
    private val sleeper = lock.writeLock().newCondition()

    companion object {
        private val logger: Logger = contextLogger()
    }

    fun subscribe(consumer: Consumer) {
        consumers.computeIfAbsent(consumer) {
            ConcurrentHashMap.newKeySet()
        }
        repartition()
        ReadLoop(consumer).loop()
    }

    inner class ReadLoop(private val consumer: Consumer) {
        private fun readRecords(): Map<Partition, Collection<RecordMetadata>>? {
            return lock.readLock().withLock {
                consumers[consumer]?.map { partition ->
                    val offset = commitments[partition] ?: when (consumer.offsetStrategy) {
                        OffsetStrategy.LATEST -> partition.latestOffset()
                        OffsetStrategy.EARLIEST -> 0L
                    }
                    partition to partition.getRecordsFrom(offset)
                }?.filter {
                    it.second.isNotEmpty()
                }?.toMap()
            }
        }

        private fun processRecords(records: Map<Partition, Collection<RecordMetadata>>?) {
            if ((records != null) && (records.isNotEmpty())) {
                @Suppress("TooGenericExceptionCaught")
                try {
                    consumer.handleRecords(records.values.flatten())
                    records.forEach { record ->
                        commitments[record.key] = record.value.maxOf { it.offset }
                    }
                } catch (e: Exception) {
                    val recordsAsString = records.values
                        .flatten().joinToString {
                            "${it.partition}/${it.offset}"
                        }
                    logger.warn(
                        "Error processing records for consumer $groupName, topic $topicName. " +
                            "Will try again records ($recordsAsString)",
                        e
                    )
                }
            } else {
                waitForDate()
            }
        }

        internal fun loop() {
            while (consumers.containsKey(consumer)) {
                processRecords(readRecords())
            }
        }
    }

    private fun waitForDate() {
        lock.writeLock().withLock {
            sleeper.await(1, TimeUnit.HOURS)
        }
    }

    fun unsubscribe(consumer: Consumer) {
        val partitions = consumers.remove(consumer)
        if (partitions != null) {
            consumer.partitionAssignmentListener?.onPartitionsUnassigned(partitions.map { topicName to it.partitionId })
            if (consumers.isNotEmpty()) {
                repartition()
            } else {
                wakeUp()
            }
        }
    }

    internal fun wakeUp() {
        lock.writeLock().withLock {
            sleeper.signalAll()
        }
    }

    private fun repartition() {
        lock.writeLock().withLock {
            partitions.withIndex().groupBy {
                it.index % consumers.size
            }.map { entry ->
                entry.value.map { it.value }
            }.zip(consumers.keys).forEach { (newPartitionList, consumer) ->
                val listener = consumer.partitionAssignmentListener
                if (listener != null) {
                    val oldPartitionList = consumers[consumer] ?: emptyList()
                    val assigned = newPartitionList - oldPartitionList
                    val unassigned = oldPartitionList - newPartitionList
                    if (unassigned.isNotEmpty()) {
                        listener.onPartitionsUnassigned(unassigned.map { topicName to it.partitionId })
                    }
                    if (assigned.isNotEmpty()) {
                        listener.onPartitionsAssigned(assigned.map { topicName to it.partitionId })
                    }
                }
                consumers[consumer] = newPartitionList
            }
            sleeper.signalAll()
        }
    }
}
