package net.corda.messaging.kafka.subscription.consumer.wrapper

import java.time.Clock

/**
 * Wrapper class to encapsulate the state and event consumers and handle any interactions with the in-memory state objects.
 */
interface StateAndEventConsumer<K : Any, S : Any, E : Any> : AutoCloseable {

    /**
     * Get the in memory state value for a given [key]
     */
    fun getValue(key: K): S?

    /**
     * Update the in memory state map with the latest state values from the message source.
     * This call will also check to see if the [eventConsumer] and [stateConsumer]
     * are in sync. If the [stateConsumer] has caught up on all the states for any newly acquired partitions this call will also resume the
     * these [eventConsumer] partitions which were paused. This will also call the [StateAndEventListener] onPartitionSynced() method.
     */
    fun updateStatesAndSynchronizePartitions()

    /**
     * Update the in memory state map with the given [updatedStates].
     */
    fun onProcessorStateUpdated(updatedStates: MutableMap<Int, MutableMap<K, S?>>, clock: Clock)

    /**
     * Direct access to [eventConsumer] and [stateConsumer]
     */
    val eventConsumer: CordaKafkaConsumer<K, E>
    val stateConsumer: CordaKafkaConsumer<K, S>
}
