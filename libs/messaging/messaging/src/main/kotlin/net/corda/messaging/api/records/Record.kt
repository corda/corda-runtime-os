package net.corda.messaging.api.records

/**
 * Object to encapsulate the events stored on topics
 * @property topic Defines the id of the topic the record is stored on.
 * @property key Is the unique per topic key for a record
 * @property value The value of the record
 * @property headers Optional list of headers to added to the message.
 * @property tracing Optional reference to the tracing API for this record.
 */
data class Record<K : Any, V : Any>(
    val topic: String,
    val key: K,
    val value: V?,
    val headers: List<Pair<String, String>> = listOf(),
    val timestamp: Long = 0
)
