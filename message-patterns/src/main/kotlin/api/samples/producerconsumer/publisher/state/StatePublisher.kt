package api.samples.producerconsumer.publisher.state

import api.samples.producerconsumer.records.StateRecord

//May not be exposed if states just an internal implementation detail of actor model
interface StatePublisher<K, V> {

    fun publish(stateRecord: StateRecord<K, V>) : Boolean
}