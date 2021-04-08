package api.samples.producerconsumer.processor

import api.samples.producerconsumer.records.event.EventRecord
import api.samples.producerconsumer.records.state.StateRecord

interface ActorProcessor<K, S, E> {
    fun onNext(state: StateRecord<K, S>, event: EventRecord<K, E>): Pair<StateRecord<K, S>,List<EventRecord<K, E>>>

    fun onError(state: StateRecord<K, S>, event: EventRecord<K, E>, e: Exception)
}