package api.samples.producerconsumer.publisher

import api.samples.producerconsumer.records.StateRecord

interface EventPublisher<EK, EV> {

    fun publish(stateRecord: StateRecord<EK, EV>) : Boolean
}