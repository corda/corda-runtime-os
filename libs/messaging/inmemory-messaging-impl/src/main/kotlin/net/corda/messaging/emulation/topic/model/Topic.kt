package net.corda.messaging.emulation.topic.model

import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.records.Record
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import org.slf4j.Logger
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.collections.HashMap
import kotlin.math.abs
import kotlin.math.max

/**
 * Object to store records and track consumer group offsets.
 * Topics have a maximum size. When max size is reached oldest records are deleted.
 * Topics have locks which must be obtained to read or write.
 * Consumers subscribing and producers writing to topics automatically create topics if they do not exist.
 */
class Topic (private val topicName : String, private val maxSize: Int) {

    companion object {
        private val log: Logger = contextLogger()
    }

    val lock = ReentrantLock()
    private var newestRecordOffset = 0L
    private var oldestRecordOffset = 0L
    private val records = LinkedList<RecordMetadata>()

    //tracks the consumerGroup offset. The offset is the offset of the next record to be retrieved
    private val consumerGroupOffset = HashMap<String, Long>()

    /**
     * Subscribe the [consumerGroup] to this [topicName] applying the [offsetStrategy]
     */
    fun subscribe(consumerGroup: String, offsetStrategy: OffsetStrategy) {
        when (offsetStrategy) {
            OffsetStrategy.EARLIEST -> {
                consumerGroupOffset[consumerGroup] = oldestRecordOffset
            }
            OffsetStrategy.LATEST -> {
                consumerGroupOffset[consumerGroup] = newestRecordOffset
            }
        }
    }

    /**
     * Add this [record] to this [topicName].
     * If [records] max size is reached, delete the oldest record
     */
    fun addRecord(record: Record<*, *>) {
        if (records.size == maxSize) {
            records.removeFirst()
            oldestRecordOffset = records.first.offset
            log.debug("hello") { "Max record count reached for topic $topicName. Deleting oldest record with offset $oldestRecordOffset." }
        }
        records.add(RecordMetadata(newestRecordOffset, record))
        newestRecordOffset++
    }

    /**
     * Get [pollSize] number of records from the [topicName] for this [consumerGroup].
     * If [autoCommitOffset] is set to true then update the [consumerGroupOffset] with the latest poll position.
     */
    fun getRecords(consumerGroup : String, pollSize: Int, autoCommitOffset: Boolean) : List<RecordMetadata> {
        var consumerOffset = consumerGroupOffset[consumerGroup]
            ?: throw CordaMessageAPIFatalException("Consumer $consumerGroup is not subscribed to ")

        if (records.isEmpty()) {
            return emptyList()
        }

        val nextRecordIndex = getConsumerNextRecordIndex(consumerOffset, consumerGroup)
        if (nextRecordIndex > records.size) {
            return emptyList()
        }
        val iterator = records.listIterator(nextRecordIndex)
        val polledRecords = mutableListOf<RecordMetadata>()

        repeat(pollSize) {
            if (iterator.hasNext()) {
                val record = iterator.next()
                polledRecords.add(record)
                consumerOffset = max(consumerOffset, record.offset) + 1
            }
        }

        if (autoCommitOffset) {
            commitOffset(consumerGroup, consumerOffset)
        }

        return polledRecords
    }

    /**
     * Commit the [offset] of a record for this [consumerGroup]
     * to the [topicName]
     */
    fun commitOffset(consumerGroup: String, offset: Long) {
        consumerGroupOffset[consumerGroup] = offset
    }

    /**
     * Calculate the index in [records] for the [consumerGroup] offset.
     * Log a warning if there records missed due to records max size reached.
     */
    private fun getConsumerNextRecordIndex(consumerOffset: Long, consumerGroup: String): Int {
        val oldestRecordOffset = records.first.offset

        var indexOffset = (consumerOffset - oldestRecordOffset).toInt()
        if (indexOffset < 0) {
            log.warn ("Records beginning at offset $consumerOffset for consumer $consumerGroup on topic $topicName were " +
                    "deleted. Record loss count ${abs(indexOffset)}")
            indexOffset = 0
        }

        return indexOffset
    }
}
