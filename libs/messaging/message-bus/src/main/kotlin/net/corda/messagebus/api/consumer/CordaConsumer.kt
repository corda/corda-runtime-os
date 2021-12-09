package net.corda.messagebus.api.consumer

import net.corda.messagebus.api.TopicPartition
import java.time.Duration


/**
 * CordaConsumers connect to and receive messages from the message bus.
 */
@Suppress("TooManyFunctions")
interface CordaConsumer<K : Any, V : Any> : AutoCloseable {

    /**
     * Subscribe to given [topics]. If not null, attach the rebalance [listener] to the [Consumer].
     * If a recoverable error occurs retry. If max retries is exceeded or a fatal error occurs then
     * a fatal exception is thrown.
     *
     * @param topics the topics to subscribe to
     * @param listener the [CordaConsumerRebalanceListener] handler for rebalance events
     */
    fun subscribe(topics: Collection<String>, listener: CordaConsumerRebalanceListener? = null)

    /**
     * Subscribe to given [topic]. If not null, attach the rebalance [listener] to the [Consumer].
     * If a recoverable error occurs retry. If max retries is exceeded or a fatal error occurs then
     * a fatal exception is thrown.
     *
     * @param topic the topic to subscribe to
     * @param listener the [CordaConsumerRebalanceListener] handler for rebalance events
     */
    fun subscribe(topic: String, listener: CordaConsumerRebalanceListener? = null)

    /**
     * Manually assign a list of partitions to this consumer. This interface does not allow for incremental assignment
     * and will replace the previous assignment (if there is one).
     *
     * @param partitions The list of partitions to assign this consumer
     */
    fun assign(partitions: Collection<TopicPartition>)

    /**
     * Get the set of partitions currently assigned to this consumer. If subscription happened by directly assigning
     * partitions using {@link #assign(Collection)} then this will simply return the same partitions that
     * were assigned. If topic subscription was used, then this will give the set of topic partitions currently assigned
     * to the consumer (which may be none if the assignment hasn't happened yet, or the partitions are in the
     * process of getting reassigned).
     *
     * @return The set of partitions currently assigned to this consumer
     */
    fun assignment(): Set<TopicPartition>

    /**
     * Get the offset of the <i>next record</i> that will be fetched (if a record with that offset exists).
     *
     * @param partition The partition to get the position for
     * @return The current position of the consumer (that is, the offset of the next record to be fetched)
     */
    fun position(partition: TopicPartition): Long

    /**
     * Overrides the fetch offsets that the consumer will use on the next [poll] of the given [partition].
     *
     * @param partition the partition which will be returned to the first offset
     * @param offset the new offset of the partition the consumer will use
     */
    fun seek(partition: TopicPartition, offset: Long)

    /**
     * Seek to the first offset for each of the given [partitions]. This function evaluates lazily, seeking to the
     * first offset in all partitions only when [poll] or [position] are called.
     * If no partitions are provided, seek to the first offset for all the currently assigned partitions.
     *
     * @param partitions the partitions which will be returned to the first offset
     */
    fun seekToBeginning(partitions: Collection<TopicPartition>)

    /**
     * Seek to the last offset for each of the given [partitions]. This function evaluates lazily, seeking to the
     * final offset in all partitions only when [poll] or [position] are called.
     * If no partitions are provided, seek to the first offset for all the currently assigned partitions.
     *
     * @param partitions the partitions which will be returned to the last offset
     */
    fun seekToEnd(partitions: Collection<TopicPartition>)

    /**
     * Get the first offset for the given [partitions].
     * This method does not change the current consumer position of the partitions.
     *
     * @param partitions the partitions to get the earliest offsets
     * @return The earliest available offsets for the given partitions
     */
    fun beginningOffsets(partitions: Collection<TopicPartition>): Map<TopicPartition, Long>

    /**
     * Get end offsets for the given [partitions].
     * This method does not change the current consumer position of the partitions.
     *
     * @param partitions the partitions to get the end offsets.
     * @return The end offsets for the given partitions.
     */
    fun endOffsets(partitions: Collection<TopicPartition>): Map<TopicPartition, Long>

    /**
     * Resume specified [partitions] which have been paused with [pause]. New calls to
     * [poll] will return records from these partitions if there are any to be fetched.
     * If the partitions were not previously paused, this method is a no-op.
     *
     * @param partitions The partitions which should be resumed
     */
    fun resume(partitions: Collection<TopicPartition>)

    /**
     * Suspend fetching from the requested [partitions]. Future calls to [poll] will not return
     * any records from these partitions until they have been resumed using [resume].
     *
     * @param partitions The partitions which should be paused
     */
    fun pause(partitions: Collection<TopicPartition>)

    /**
     * Get the set of partitions that were previously paused by a call to [pause].
     */
    fun paused(): Set<TopicPartition>

    /**
     * Poll records from the consumer and sort them by timestamp
     */
    fun poll(): List<CordaConsumerRecord<K, V>>

    /**
     * Poll records from the consumer and sort them by timestamp with a [timeout]
     *
     * @param timeout The maximum time to block (must not be greater than {@link Long#MAX_VALUE} milliseconds)
     */
    fun poll(timeout: Duration): List<CordaConsumerRecord<K, V>>

    /**
     * Reset the consumer position on a topic to the last committed position. Next poll from the topic will
     * read from this position. If no position is found for this consumer on the topic then apply the [offsetStrategy].
     *
     * @param offsetStrategy the strategy to apply when no last committed position exists
     */
    fun resetToLastCommittedPositions(offsetStrategy: CordaOffsetResetStrategy)

    /**
     * Synchronously commit the consumer offset for this [event] back to the topic partition.
     * Record [metaData] about this commit back on the [event] topic.
     * @throws CordaMessageAPIFatalException fatal error occurred attempting to commit offsets.
     */
    fun commitSyncOffsets(event: CordaConsumerRecord<K, V>, metaData: String? = null)

    /**
     * Get metadata about the partitions for a given topic.
     *
     * @param topic The topic to get partition metadata for
     * @param timeout The maximum of time to await topic metadata
     *
     * @return The list of [TopicPartition]s
     */
    fun getPartitions(topic: String, timeout: Duration): List<TopicPartition>

    /**
     * Tries to close the consumer cleanly within the specified timeout. This method waits up to
     * [timeout] for the consumer to complete pending commits and leave the group.
     * If the consumer is unable to complete offset commits and gracefully leave the group
     * before the timeout expires, the consumer is force closed.
     *
     * @param timeout The maximum time to wait for consumer to close gracefully. The value must be
     *                non-negative. Specifying a timeout of zero means do not wait for pending requests to complete.
     */
    fun close(timeout: Duration)
}
