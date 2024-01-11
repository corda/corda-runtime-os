package net.corda.messaging.mediator

import net.corda.messaging.api.mediator.config.EventMediatorConfig
import net.corda.messaging.api.records.Record

/**
 * Helper class to use in the mediator to divide polled records into groups for processing.
 */
class GroupAllocator {

    /**
     * Allocate events into groups based on their keys, a configured minimum group size and thread count.
     * This allows for more efficient multi-threaded processing.
     * The threshold record count to establish a new group is [config.minGroupSize].
     * If the number of groups exceeds the number of threads then the group count is set to the number of [config.threads]
     * Records of the same key are always placed into the same group regardless of group size and count.
     * @param events Events to allocate to groups
     * @param config Mediator config
     * @return Records allocated to groups.
     */
    fun <K : Any, S : Any, E : Any> allocateGroups(
        events: List<Record<K, E>>,
        config: EventMediatorConfig<K, S, E>
    ): List<Map<K, List<Record<K, E>>>> {
        val groups = setUpGroups(config)
        val buckets = events
            .groupBy { it.key }.toList()
            .sortedByDescending { it.second.size }
        
        buckets.forEach { (key, records) ->
            val leastFilledGroup = groups.minByOrNull { it.values.flatten().size }
            leastFilledGroup?.put(key, records)
        }
        
        return groups.filter { it.values.isNotEmpty() }
    }

    private fun <E : Any, S: Any, K : Any> setUpGroups(
        config: EventMediatorConfig<K, S, E>,
    ): MutableList<MutableMap<K, List<Record<K, E>>>> {
        val numGroups = config.threads
        return MutableList(numGroups) { mutableMapOf() }
    }
}