package api.samples.producerconsumer.publisher

import api.samples.producerconsumer.records.EventRecord
import api.samples.producerconsumer.records.StateRecord

interface Publisher<T> {

    /**
     * Generic publisher for all events.
     * Return value indicates success.
     * Impl specific guarantees on what success means (message sent v message delivered)
     */
    fun publish(eventRecord: EventRecord<T>) : Boolean

    fun publish(stateRecord: StateRecord<T>) : Boolean
}