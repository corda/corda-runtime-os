package net.corda.messagebus.api.consumer

import net.corda.messagebus.api.CordaTopicPartition

/**
 * A callback interface that the user can implement to trigger custom actions when the set of partitions assigned to the
 * consumer changes.
 */
interface CordaConsumerRebalanceListener {

    /**
     * This method will be called during a rebalance operation when the consumer has to give up some partitions.
     * It can also be called when consumer is being closed or is unsubscribing.
     * It is recommended that offsets should be committed in this callback to prevent duplicate data.
     *
     * @param partitions The list of partitions that were assigned to the consumer and now need to be revoked (may not
     *                   include all currently assigned partitions, i.e. there may still be some partitions left)
     *
     */
    fun onPartitionsRevoked(partitions: Collection<CordaTopicPartition>)

    /**
     * This method will be called after the partition re-assignment completes and before the consumer
     * starts fetching data, and only as the result of a poll(long) call.
     * <p>
     * It is guaranteed that under normal conditions all the processes in a consumer group will execute their
     * [onPartitionsRevoked] callback before any instance executes its [onPartitionsAssigned] callback.
     * During exceptional scenarios, partitions may be migrated without the old owner being notified (i.e. their
     * [onPartitionsRevoked] callback not triggered), and later when the old owner consumer realized this event,
     * the [onPartitionsLost] callback will be triggered by the consumer then.
     *
     * @param partitions The list of partitions that are now assigned to the consumer (previously owned partitions will
     *                   NOT be included, i.e. this list will only include newly added partitions)
     */
    fun onPartitionsAssigned(partitions: Collection<CordaTopicPartition>);

    /**
     * This method will not be called during normal execution as the owned partitions would
     * first be revoked by calling the [onPartitionsRevoked], before being reassigned
     * to other consumers during a rebalance event. However, during exceptional scenarios when the consumer realizes
     * that it does not own this partition any longer, i.e. not revoked via a normal rebalance event, then this method
     * would be invoked. By default, it will just trigger [onPartitionsRevoked].
     *
     * @param partitions The list of partitions that were assigned to the consumer and now have been reassigned
     *                   to other consumers. With the current protocol this will always include all the consumer's
     *                   previously assigned partitions, but this may change in future protocols (ie there would still
     *                   be some partitions left)
     */
    fun onPartitionsLost(partitions: Collection<CordaTopicPartition>) = onPartitionsRevoked(partitions)
}
