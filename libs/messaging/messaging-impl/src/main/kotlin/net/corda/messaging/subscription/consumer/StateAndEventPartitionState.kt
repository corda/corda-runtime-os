package net.corda.messaging.subscription.consumer

/**
 * Wrapper data class to store the in-memory state map [currentStates]
 */
data class StateAndEventPartitionState<K : Any, S : Any>(
    /**
     * Maps of partition id to a map of keys/states and the timestamp at which it was read from kafka.
     */
    val currentStates: MutableMap<Int, MutableMap<K, S>>,

    /**
     * Used to indicate the values have changed.
     */
    var dirty: Boolean = false
)
