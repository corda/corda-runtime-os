package api.samples.producerconsumer.records.state

class StateRecord<K, V>(val stateTopic: String, val key: K, val value: V)
