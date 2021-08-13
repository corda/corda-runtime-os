package net.corda.messaging.emulation.topic.model

import net.corda.messaging.api.records.Record
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import java.util.LinkedList
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock

internal class Partition(
    val partitionId: Int,
    private val maxSize: Int,
    private val topicName: String,
) {
    companion object {
        private val logger = contextLogger()
    }

    private val lock = ReentrantReadWriteLock()
    private val records = LinkedList<RecordMetadata>()
    private val currentOffset = AtomicLong(0)

    fun addRecord(record: Record<*, *>) {
        lock.writeLock().withLock {
            if (records.size >= maxSize) {
                val deletedRecord = records.removeFirst()
                logger.debug {
                    "Max record count reached for topic $topicName/$partitionId." +
                        " Deleting oldest record with offset ${deletedRecord.offset}."
                }
            }
            records.add(RecordMetadata(currentOffset.incrementAndGet(), record, partitionId))
        }
    }

    fun getRecordsFrom(fromOffset: Long, pollSize: Int): Collection<RecordMetadata> {
        return lock.readLock().withLock {
            records
                .asSequence()
                .dropWhile { it.offset <= fromOffset }
                .take(pollSize)
                .toList()
        }
    }

    fun latestOffset(): Long {
        return currentOffset.get()
    }

    fun handleAllRecords(handler: (Sequence<RecordMetadata>) -> Unit) {
        lock.readLock().withLock {
            handler(records.asSequence())
        }
    }
}
