package net.corda.messaging.impl.subscription.subscriptions.pubsub.service.model

import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.records.Record
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.IllegalStateException
import java.util.*
import kotlin.collections.HashMap
import kotlin.math.abs

class Topic (private val name : String, private val maxSize: Int) {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

    private var currentOffset = 0
    private var oldest = 0
    private val records = LinkedList<RecordMetadata>()
    private val consumerPositions = HashMap<String, Int>()

    fun subscribe(consumerGroup: String, offsetStrategy: OffsetStrategy) {
        when (offsetStrategy) {
            OffsetStrategy.EARLIEST -> {
                consumerPositions[consumerGroup] = oldest
            }
            OffsetStrategy.LATEST -> {
                consumerPositions[consumerGroup] = currentOffset
            }
        }
    }

    fun addRecord(record: Record<*, *>) {
        if (records.size == maxSize) {
            records.removeFirst()
            oldest = records.first.offset
            log.warn("Max record count reached for topic $name. Deleting oldest record.")
        }
        currentOffset++
        records.add(RecordMetadata(currentOffset, record))
    }

    fun getRecords(consumerGroup : String, pollSize: Int) : List<Record<*, *>> {
        var consumerOffset = consumerPositions[consumerGroup]
            ?: throw CordaMessageAPIFatalException("Consumer not subscribed" , IllegalStateException())
        val currentConsumerIndex = getCurrentConsumerIndex(consumerOffset, consumerGroup)
        val iterator = records.listIterator(currentConsumerIndex + 1)
        val polledRecords = mutableListOf<Record<*, *>>()

        repeat(pollSize) {
            if (iterator.hasNext()) {
                polledRecords.add(iterator.next().record)
                consumerOffset++
            }
        }

        consumerPositions[consumerGroup] = consumerOffset
        return polledRecords
    }

    private fun getCurrentConsumerIndex(consumerOffset: Int, consumerGroup: String): Int {
        val oldestRecordIndex = records.indexOf(records.first)
        val oldestRecordOffset = records.first.offset

        var indexOffset = consumerOffset - oldestRecordOffset
        if (indexOffset < 0) {
            log.warn("Records beginning at offset $consumerOffset for consumer $consumerGroup on topic $name were " +
                    "deleted. Record loss count ${abs(indexOffset)}")
            indexOffset = 0
        }

        return oldestRecordIndex + indexOffset
    }

    fun unsubscribe(consumerGroup: String) {
        consumerPositions.remove(consumerGroup)
    }
}
