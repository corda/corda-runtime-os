package net.corda.p2p.gateway.messaging.internal

import net.corda.messaging.api.subscription.PartitionAssignmentListener

/**
 * Mock class for now. Should be properly implemented when Gateway evolves to be more partition aware
 */
class PartitionAssignmentListenerImpl : PartitionAssignmentListener {
    override fun onPartitionsUnassigned(topicPartitions: List<Pair<String, Int>>) {
        TODO("Not yet implemented")
    }

    override fun onPartitionsAssigned(topicPartitions: List<Pair<String, Int>>) {
        TODO("Not yet implemented")
    }
}