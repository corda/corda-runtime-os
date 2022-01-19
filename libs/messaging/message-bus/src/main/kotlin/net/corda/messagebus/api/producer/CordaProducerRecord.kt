package net.corda.messagebus.api.producer

/**
 * Object to encapsulate the events stored on topics
 * @property topic defined the id of the topic the record is stored on.
 * @property key is the unique per topic key for a record
 * @property value the value of the record
 */
data class CordaProducerRecord<K : Any, V : Any>(val topic: String, val key: K, val value: V?)
