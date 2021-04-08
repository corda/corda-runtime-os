package api.samples.producerconsumer.publisher.event

import api.samples.producerconsumer.records.state.StateRecord

interface EventPublisher<EK, EV> {

    fun publish(stateRecord: StateRecord<EK, EV>) : Boolean
}