package net.corda.messagebus.api.subscription

/**
 * An interface that can be implemented for a subscription to react to assignment and revocation
 * of topic partitions.  This will be useful for handling rebalancing situations on the Kafka consumers and
 * will allow the subscription to ensure consistency after a rebalance event.
 */
interface PartitionAssignmentListener {
    /**
     * Implement this method to handle the removal of topic partitions
     *
     * @param topicPartitions the topic partitions that were unassigned.
     */
    fun onPartitionsUnassigned(topicPartitions: List<Pair<String, Int>>)

    /**
     * Implement this method to handle the addition of topic partitions
     *
     * @param topicPartitions the topic partitions that were assigned.
     */
    fun onPartitionsAssigned(topicPartitions: List<Pair<String, Int>>)
}
