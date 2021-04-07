package api.samples.producerconsumer.publisher

import api.samples.producerconsumer.records.EventRecord
import api.samples.producerconsumer.records.StateRecord

interface Publisher {

    /**
     * Generic publisher for all events.
     * Return value indicates success.
     * Impl specific guarantees on what success means (message sent v message delivered)
     */
    fun publish(eventRecord: EventRecord) : Boolean

    fun publish(stateRecord: StateRecord) : Boolean
}