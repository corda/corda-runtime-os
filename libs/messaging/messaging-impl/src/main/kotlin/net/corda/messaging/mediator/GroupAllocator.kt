package net.corda.messaging.mediator

import net.corda.messaging.api.mediator.config.EventMediatorConfig
import net.corda.messaging.api.records.Record
import kotlin.math.ceil

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
        val groups = setUpGroups(config, events)
        val buckets = events.groupBy { it.key }
        val bucketsSized = buckets.keys.sortedByDescending { buckets[it]?.size }
        for (i in 0 until buckets.size) {
            val group = groups.minBy { it.values.flatten().size }
            val key = bucketsSized[i]
            group[key] = buckets[key]!!
        }

        return groups.filter { it.values.isNotEmpty() }
    }

    private fun <E : Any, S: Any, K : Any> setUpGroups(
        config: EventMediatorConfig<K, S, E>,
        events: List<Record<K, E>>
    ): MutableList<MutableMap<K, List<Record<K, E>>>> {
        val groups = mutableListOf<MutableMap<K, List<Record<K, E>>>>()
        val threadCount = config.threads
        val groupCountBasedOnEvents = ceil(events.size.toDouble() / config.minGroupSize).toInt()
        val groupsCount = if (groupCountBasedOnEvents < threadCount) groupCountBasedOnEvents else threadCount
        for (i in 0 until groupsCount) {
            groups.add(mutableMapOf())
        }
        return groups
    }
}