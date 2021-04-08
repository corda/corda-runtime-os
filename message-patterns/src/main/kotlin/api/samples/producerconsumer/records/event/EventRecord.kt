package api.samples.producerconsumer.records.event

class EventRecord<K, V>(val eventTopic: String, val key: K, val value: V)
