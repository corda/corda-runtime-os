package api.samples.producerconsumer.records.state

//May not be exposed if states just an internal implementation detail of actor model
class StateRecord<K, V>(val stateTopic: String, val key: K, val value: V)
