package net.corda.messaging.mediator

import net.corda.messaging.api.mediator.config.EventMediatorConfig

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
        events: List<EventProcessingInput<K, E>>,
        config: EventMediatorConfig<K, S, E>
    ): List<Map<K, EventProcessingInput<K, E>>> {
        val eventCount = events.flatMap { it.records }.size.toDouble()
        val groups = setUpGroups(config)
        val sortedEvents = events.sortedByDescending { it.records.size }
        sortedEvents.forEach {
            val leastFilledGroup = groups.minBy { group ->
                group.flatMap { (_, input) -> input.records }.size
            }
            leastFilledGroup[it.key] = it
        }

        return groups.filter { it.isNotEmpty() }
    }

    private fun <E : Any, S: Any, K : Any> setUpGroups(
        config: EventMediatorConfig<K, S, E>,
    ): List<MutableMap<K, EventProcessingInput<K, E>>> {
        val numGroups = config.threads
        return List(numGroups) { mutableMapOf() }
    }
}