package net.corda.messaging.api.records

/**
 * Object to encapsulate the events stored on topics
 * [topic] defined the id of the topic the record is stored on.
 * [key] is the unique per topic key for a record
 * [value] the value of the record
 */
class Record<K, V>(val topic: String, val key: K, val value: V)
