package net.corda.messaging.kafka.subscription.consumer.wrapper

/**
 * Wrapper data class to store the in-memory state map [currentStates]
 * and a [partitionsToSync] map to keep track of the state partitions that need to be synced.
 */
data class StateAndEventPartitionState<K : Any, S : Any>(
    val currentStates: MutableMap<Int, MutableMap<K, Pair<Long, S>>>,
    val partitionsToSync: MutableMap<Int, Long>
)
