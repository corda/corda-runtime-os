package net.corda.messaging.emulation.topic.model

import net.corda.messaging.api.records.Record
import net.corda.v5.base.util.debug
import org.slf4j.LoggerFactory
import java.util.LinkedList
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read

internal class Partition(
    val partitionId: Int,
    private val maxSize: Int,
    val topicName: String,
) {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    val lock = ReentrantReadWriteLock()
    private val records = LinkedList<RecordMetadata>()
    private val currentOffset = AtomicLong(0)

    fun addRecord(record: Record<*, *>) {
        if (!lock.isWriteLocked) {
            throw ConcurrentModificationException("The partition should be locked for write from outside")
        }
        if (records.size >= maxSize) {
            val deletedRecord = records.removeFirst()
            logger.debug {
                "Max record count reached for topic $topicName/$partitionId." +
                    " Deleting oldest record with offset ${deletedRecord.offset}."
            }
        }
        records.add(RecordMetadata(currentOffset.getAndIncrement(), record, partitionId))
    }

    fun getRecordsFrom(fromOffset: Long, pollSize: Int): Collection<RecordMetadata> {
        return lock.read {
            records
                .asSequence()
                .dropWhile { it.offset < fromOffset }
                .take(pollSize)
                .toList()
        }
    }

    fun latestOffset(): Long {
        return currentOffset.get()
    }
}
