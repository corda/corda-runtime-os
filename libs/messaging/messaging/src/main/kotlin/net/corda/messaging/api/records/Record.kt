package net.corda.messaging.api.records

/**
 * Object to encapsulate the events stored on topics
 * @property topic defined the id of the topic the record is stored on.
 * @property key is the unique per topic key for a record
 * @property value the value of the record
 */
open class Record<K, V>(val topic: String, val key: K, val value: V?)
