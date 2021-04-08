package api.samples.producerconsumer.publisher

import api.samples.producerconsumer.records.StateRecord

interface StatePublisher<SK, SV> {

    fun publish(stateRecord: StateRecord<SK, SV>) : Boolean
}