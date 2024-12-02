package net.corda.messaging.api.subscription.listener

/**
 * The [ConsumerOffsetProvider] allows a consumer client to provide a custom source of starting offsets. This is useful
 * in scenarios where the client code want's to manage the consumers itself.
*/
interface ConsumerOffsetProvider {

    /**
     * Called by the consumer whenever it needs to know the starting offsets for a set of topic partitions.
     *
     * Note: This method will always be called on the polling thread.
     * @param topicPartitions A list of topic partitions to provide offsets for
     * @return A list of offsets for each requested topic partition.
     */
    fun getStartingOffsets(topicPartitions: Set<Pair<String, Int>>): Map<Pair<String, Int>, Long>
}