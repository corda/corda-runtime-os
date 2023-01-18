package net.corda.messaging.subscription.consumer

import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.subscription.listener.StateAndEventListener
import java.time.Clock
import java.util.concurrent.CompletableFuture
import kotlin.jvm.Throws

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
    ) : CordaMessageAPIIntermittentException(message, exception)

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
    fun updateInMemoryStatePostCommit(updatedStates: MutableMap<Int, MutableMap<K, S?>>, clock: Clock)

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
     * TODO: can cause issues if used carelessly, we should remove the direct access and manage the consumers
     *       internally only (providing adapter methods where necessary).
     */
    val eventConsumer: CordaConsumer<K, E>
    val stateConsumer: CordaConsumer<K, S>
}
