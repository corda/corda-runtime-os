package net.corda.messagebus.db.util

import net.corda.messagebus.api.CordaTopicPartition

/**
 * Utility class for keeping track of the current offsets, stored by [CordaTopicPartition].
 */
class LatestOffsets(
    initialState: Map<CordaTopicPartition, Long>
) {
    companion object {
        fun build(initialState: Map<String, Map<Int, Long>>): LatestOffsets {
            val stateForObject = mutableMapOf<CordaTopicPartition, Long>()
            initialState.forEach { (topic, partitionOffsetMap) ->
                partitionOffsetMap.forEach { (partition, offset) ->
                    stateForObject[CordaTopicPartition(topic, partition)] = offset
                }
            }
            return LatestOffsets(stateForObject)
        }
    }

    private val latestOffsets: MutableMap<CordaTopicPartition, Long> = initialState.toMutableMap()

    fun getNextOffsetFor(topicPartition: CordaTopicPartition): Long {
        val offset = latestOffsets.computeIfAbsent(topicPartition) { 0 }
        latestOffsets[topicPartition] = offset + 1
        return offset
    }
}
