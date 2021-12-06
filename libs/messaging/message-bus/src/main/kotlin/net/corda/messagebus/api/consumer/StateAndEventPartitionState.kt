package net.corda.messagebus.api.consumer

/**
 * Wrapper data class to store the in-memory state map [currentStates]
 * and a [partitionsToSync] map to keep track of the state partitions that need to be synced and their latest offset.
 */
data class StateAndEventPartitionState<K : Any, S : Any>(
    /**
     * Maps of partition id to a map of keys/states and the timestamp at which it was read.
     */
    val currentStates: MutableMap<Int, MutableMap<K, Pair<Long, S>>>,

    /**
     * Map of partition id to offset. The offset is the latest offset for the partition.
     */
    val partitionsToSync: MutableMap<Int, Long>
)
