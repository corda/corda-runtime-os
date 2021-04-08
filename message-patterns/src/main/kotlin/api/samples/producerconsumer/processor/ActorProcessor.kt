package api.samples.producerconsumer.processor

import api.samples.producerconsumer.records.EventRecord
import api.samples.producerconsumer.records.StateRecord

interface ActorProcessor<K, SV, EV> {

    fun onNext(state: StateRecord<K, SV>, event: EventRecord<K, EV>): Pair<StateRecord<K, SV>,List<EventRecord<K, EV>>>

    fun onError(state: StateRecord<K, SV>, event: EventRecord<K, EV>, e: Exception)
}