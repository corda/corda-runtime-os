package net.corda.messaging.subscription.consumer

import net.corda.messagebus.api.consumer.CordaConsumer
import java.time.Clock
import java.util.concurrent.CompletableFuture

/**
 * Wrapper class to encapsulate the state and event consumers and handle any interactions with the in-memory state objects.
 */
interface StateAndEventConsumer<K : Any, S : Any, E : Any> : AutoCloseable {

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
     * @return false when a rebalance occurred and any current messages should be abandoned.
     */
    fun resetPollInterval() : Boolean

    /**
     * Run a [function] and return a future with the result of the function.
     * [function] will be allowed to run for a [maxTimeout] after it exceeds the default poll interval timeout.
     * This will pause consumers and poll at regular intervals to avoid being kicked from the consumer group.
     */
    fun waitForFunctionToFinish(function: () -> Any, maxTimeout: Long, timeoutErrorMessage: String) : CompletableFuture<Any>

    /**
     * Direct access to [eventConsumer] and [stateConsumer]
     */
    val eventConsumer: CordaConsumer<K, E>
    val stateConsumer: CordaConsumer<K, S>
}
