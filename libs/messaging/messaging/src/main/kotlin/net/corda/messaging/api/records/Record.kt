package net.corda.messaging.api.records

/**
 * Object to encapsulate the events stored on topics
 * @property topic Defines the id of the topic the record is stored on.
 * @property key Is the unique per topic key for a record
 * @property value The value of the record
 * @property timestamp The timestamp of when the message was produced.
 * @property headers Optional list of headers to added to the message.
 */
data class Record<K : Any, V : Any>(
    val topic: String?,
    val key: K,
    val value: V?,
    val timestamp: Long = 0,
    val headers: List<Pair<String, String>> = listOf()
)
