package net.corda.messaging.api.records

/**
 * Object to encapsulate the events stored on topics
 * @property topic defined the id of the topic the record is stored on.
 * @property key is the unique per topic key for a record
 * @property value the value of the record
 */
class Record<K : Any, V : Any>(val topic: String, val key: K?, val value: V?) {
    init {
        require(key != null || value != null) { "Only the key or the value of a record can be null, not both." }
    }
}
