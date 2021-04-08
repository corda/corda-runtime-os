package api.samples.producerconsumer.processor

import api.samples.producerconsumer.records.EventRecord

interface Processor<EK, EV> {
    fun onNext(eventRecord: EventRecord<EK, EV>) : List<EventRecord<*, EV>>

    fun onError(eventRecord: EventRecord<EK, EV>, e: Exception)
}

