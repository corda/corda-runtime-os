package net.corda.messagebus.db.util

import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import java.util.concurrent.ConcurrentHashMap

/**
 * Utility class for keeping track of the current offsets, stored by [CordaTopicPartition].
 */
class WriteOffsets(
    initialState: Map<CordaTopicPartition, Long>
) {
    companion object {
        fun build(initialState: Map<String, Map<Int, Long>>): WriteOffsets {
            val stateForObject = mutableMapOf<CordaTopicPartition, Long>()
            initialState.forEach { (topic, partitionOffsetMap) ->
                partitionOffsetMap.forEach { (partition, offset) ->
                    stateForObject[CordaTopicPartition(topic, partition)] = offset
                }
            }
            return WriteOffsets(stateForObject)
        }
    }

    private val latestOffsets: MutableMap<CordaTopicPartition, Long> = ConcurrentHashMap(initialState)

    fun getNextOffsetFor(topicPartition: CordaTopicPartition): Long {
        return latestOffsets.compute(topicPartition) { _: CordaTopicPartition, offset: Long? ->
            offset?.plus(1) ?: 0L
        } ?: throw CordaMessageAPIFatalException("Next offset should never be null.")
    }
}
