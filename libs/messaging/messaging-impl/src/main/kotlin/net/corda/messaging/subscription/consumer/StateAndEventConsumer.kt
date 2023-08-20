package net.corda.messaging.subscription.consumer

import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messaging.api.subscription.listener.StateAndEventListener
import net.corda.v5.base.exceptions.CordaRuntimeException
import java.time.Clock
import java.util.concurrent.CompletableFuture

/**
 * Wrapper class to encapsulate the state and event consumers and handle any interactions with the in-memory state objects.
 */
interface StateAndEventConsumer<K : Any, S : Any, E : Any> : AutoCloseable {

    /**
     * Thrown whenever a rebalance occurs at the consumer group level while polling from the message bus through
     * the internal [eventConsumer].
     */
    class RebalanceInProgressException(
        message: String,
        exception: Exception? = null
    ) : CordaRuntimeException(message, exception)

    /**
     * Perform any management of the state and event consumers when new partitions are assigned to the event consumer.
     *
     * @param partitions The set of partitions that has been assigned to this event consumer.
     */
    fun onPartitionsAssigned(partitions: Set<CordaTopicPartition>)

    /**
     * Perform any management of the state and event consumers when partitions are revoked from the event consumer.
     *
     * @param partitions The set of partitions that have been revoked from this event consumer.
     */
    fun onPartitionsRevoked(partitions: Set<CordaTopicPartition>)

    /**
     * Poll for any events.
     *
     * @return The list of consumer records returned from the poll
     */
    fun pollEvents(): List<CordaConsumerRecord<K, E>>

    /**
     * Reset the event consumer to its last committed position.
     *
     * Should be invoked if processing of a batch of events fails to ensure that they are polled again.
     */
    fun resetEventOffsetPosition()

    /**
     * Get the in memory state value for a given [key]
     */
    fun getInMemoryStateValue(key: K): S?

    /**
     * Update the in memory state map with the latest values, calls poll on the [stateConsumer].
     * Checks to see if the [eventConsumer] and [stateConsumer] are in sync when [syncPartitions] is set to true.
     * They will be in sync when all the states on the assigned partitions (at the point of assignment) are read.
     * When the consumers are sync, resume the [eventConsumer] partitions which were paused and call the [StateAndEventListener]
     * to notify the client.
     */
    fun pollAndUpdateStates(syncPartitions: Boolean)

    /**
     * Update the in memory state map with the given [updatedStates].
     * [updatedStates] is a map of key/value pairs mapped by partition ID.
     * Updates to the in memory store are saved with a timestamp calculated using the [clock] instance.
     */
    fun updateInMemoryStatePostCommit(updatedStates: Map<Int, MutableMap<K, S?>>, clock: Clock)

    /**
     * Reset the poll interval if the consumers are close to exceeding the poll interval timeout.
     * If cutoff point is reached, the consumers are paused, poll is called, and the consumers are then resumed.
     *
     * @throws RebalanceInProgressException when a rebalance occurs while resetting the poll interval.
     */
    @Throws(RebalanceInProgressException::class)
    fun resetPollInterval()

    /**
     * Run a [function] and return a future with the result of the function.
     * [function] will be allowed to run for a [maxTimeout] after it exceeds the default poll interval timeout.
     * This will pause the [eventConsumer] and poll at regular intervals to avoid being kicked from the consumer group.
     *
     * @throws RebalanceInProgressException if a rebalance occurs during poll while waiting for the function to finish.
     */
    @Throws(RebalanceInProgressException::class)
    fun waitForFunctionToFinish(function: () -> Any, maxTimeout: Long, timeoutErrorMessage: String) : CompletableFuture<Any>

    /**
     * Direct access to [eventConsumer] and [stateConsumer].
     * @Suppress("ForbiddenComment")
     * TODO: remove direct access to consumers. See https://r3-cev.atlassian.net/browse/CORE-9569.
     */
    val eventConsumer: CordaConsumer<K, E>
    val stateConsumer: CordaConsumer<K, S>

    fun postUpdates()
}
