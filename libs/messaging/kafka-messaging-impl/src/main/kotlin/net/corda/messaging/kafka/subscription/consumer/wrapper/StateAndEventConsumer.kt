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
     * Update the in memory state map with the latest values from kafka, calls poll on the [stateConsumer].
     * Checks to see if the [eventConsumer] and [stateConsumer] are in sync.
     * They will be in sync when all the states on the assigned partitions (at the point of assignment) are read from kafka.
     * When the consumers are sync, resume the [eventConsumer] partitions which were paused and call the [StateAndEventListener]
     * to notify the client.
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
