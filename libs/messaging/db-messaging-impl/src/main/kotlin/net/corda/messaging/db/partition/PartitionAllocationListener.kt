package net.corda.messaging.db.partition

import net.corda.messaging.api.subscription.Subscription

interface PartitionAllocationListener {
    /**
     * This method is expected to be called when a set of partitions are assigned to a [Subscription].
     */
    fun onPartitionsAssigned(topic: String, partitions: Set<Int>)

    /**
     * This method is expected to be called when a set of partitions are unassigned from a [Subscription].
     */
    fun onPartitionsUnassigned(topic: String, partitions: Set<Int>)
}