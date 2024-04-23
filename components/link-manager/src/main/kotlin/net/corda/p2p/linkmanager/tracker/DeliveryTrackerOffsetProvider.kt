package net.corda.p2p.linkmanager.tracker

import net.corda.messaging.api.subscription.listener.ConsumerOffsetProvider
import kotlin.math.max

internal class DeliveryTrackerOffsetProvider(
    private val partitionStates: PartitionsStates,
) : ConsumerOffsetProvider {
    override fun getStartingOffsets(
        topicPartitions: Set<Pair<String, Int>>,
    ): Map<Pair<String, Int>, Long> {
        val topic = topicPartitions.map { it.first }.firstOrNull() ?: return emptyMap()
        val partitionsIndices = topicPartitions.map { it.second }.toSet()
        return partitionStates.loadPartitions(partitionsIndices).mapValues { (_, state) ->
            max(state.readRecordsFromOffset, 0)
        }.mapKeys { (partition, _) ->
            topic to partition
        }
    }
}
