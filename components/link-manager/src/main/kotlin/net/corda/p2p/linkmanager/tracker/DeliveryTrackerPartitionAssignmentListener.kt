package net.corda.p2p.linkmanager.tracker

import net.corda.messaging.api.subscription.listener.PartitionAssignmentListener

internal class DeliveryTrackerPartitionAssignmentListener(
    private val states: PartitionsStates,
) : PartitionAssignmentListener {
    override fun onPartitionsUnassigned(topicPartitions: List<Pair<String, Int>>) {
        val partitions = topicPartitions.map {
            it.second
        }.toSet()
        states.forgetPartitions(partitions)
    }

    override fun onPartitionsAssigned(topicPartitions: List<Pair<String, Int>>) {
        val partitions = topicPartitions.map {
            it.second
        }.toSet()
        states.loadPartitions(partitions)
    }
}
