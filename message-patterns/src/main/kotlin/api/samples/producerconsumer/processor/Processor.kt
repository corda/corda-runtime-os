package api.samples.producerconsumer.processor

import api.samples.producerconsumer.records.EventRecord

interface Processor<EK, EV> {
    /**
     * process a record
     */
    fun onNext(eventRecord: EventRecord<EK, EV>) : List<EventRecord<*, EV>>

    /**
     * When error occurs processing a record. May be unnecessary
     */
    fun onError(eventRecord: EventRecord<EK, EV>, e: Exception)
}

