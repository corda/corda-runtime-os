package api.samples.processor

import api.samples.records.Record

interface ActorProcessor<K, S, E> {
    val keyClass: Class<K>
    val stateValueClass: Class<S>
    val eventValueClass: Class<E>

    fun onNext(state: Record<K, S>, event: Record<K, E>): Pair<Record<K, S>,List<Record<*, *>>>
}