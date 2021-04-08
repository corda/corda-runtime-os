package api.samples.producerconsumer.processor

import api.samples.producerconsumer.records.EventRecord
import api.samples.producerconsumer.records.StateRecord

interface ActorProcessor<K, S, E> {
    fun onNext(state: StateRecord<K, S>, event: EventRecord<K, E>): Pair<StateRecord<K, S>,List<EventRecord<K, E>>>

    fun onError(state: StateRecord<K, S>, event: EventRecord<K, E>, e: Exception)
}