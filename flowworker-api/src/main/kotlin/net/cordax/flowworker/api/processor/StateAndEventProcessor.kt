package net.cordax.flowworker.api.processor

import net.cordax.flowworker.api.records.Record
import net.cordax.flowworker.api.records.StateAndEvent

interface StateAndEventProcessor<K, S, E> {
    val keyClass: Class<K>
    val stateValueClass: Class<S>
    val eventValueClass: Class<E>

    fun onNext(stateAndEvent: StateAndEvent<K, S, E>): Pair<Record<K, S>,List<Record<*, *>>>
}